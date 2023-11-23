use std::mem::{replace, swap};

use crate::{error::Error, Durak};
use race_api::prelude::*;

const DECK_LEN: usize = 36;
const TRUMP_IDX: usize = DECK_LEN - 1;
const MAX_ATTACK_COUNT: usize = 6;
const MIN_HAND_CARD_COUNT: usize = 6;
const MAX_NUM_OF_PLAYERS: usize = 4;
const ACT_TIMEOUT_MS: u64 = 20_000;
const RESET_TIMEOUT_MS: u64 = 30_000;
const END_OF_ROUND_TIMEOUT_MS: u64 = 10_000;

fn kind_str_to_u8(k: &str) -> u8 {
    match k {
        "a" => 14,
        "k" => 13,
        "q" => 12,
        "j" => 11,
        "t" => 10,
        s => s.parse().unwrap_or(0),
    }
}

#[derive(BorshSerialize, BorshDeserialize, Clone)]
pub struct Card {
    pub idx: usize,
    pub value: String,
}
impl Card {
    pub fn new(idx: usize, value: String) -> Self {
        Self { idx, value }
    }
    pub fn suit(&self) -> &str {
        &self.value[0..1]
    }
    pub fn kind(&self) -> &str {
        &self.value[1..2]
    }
    pub fn is_same_suit(&self, other: &Card) -> bool {
        self.suit().eq(other.suit())
    }
    pub fn is_same_kind(&self, other: &Card) -> bool {
        self.kind().eq(other.kind())
    }
    pub fn is_covered_by(&self, card: &Card) -> bool {
        self.suit().eq(card.suit()) && kind_str_to_u8(self.kind()) < kind_str_to_u8(card.kind())
    }
}

#[derive(Debug, BorshSerialize, BorshDeserialize, PartialEq, Eq)]
pub enum Role {
    Attacker,
    Defender,
    CoAttacker,
    Escaped,
}

#[derive(Debug, Default, BorshSerialize, BorshDeserialize, PartialEq, Eq, Copy, Clone)]
pub enum Stage {
    #[default]
    Waiting,
    Shuffling,
    RevealingTrump,
    Dealing,
    Acting,
    EndOfRound,
    EndOfGame,
}

#[derive(BorshSerialize, BorshDeserialize)]
pub enum Action {
    Attack {
        cards: Vec<Card>,
    },
    CoAttack {
        cards: Vec<Card>,
    },
    Defend {
        card: Card,
        target: u8, // The target card to defend
    },
    Forward {
        card: Card,
    },
    Take,
    Beated,
}
impl CustomEvent for Action {}

#[derive(BorshSerialize, BorshDeserialize)]
pub struct Player {
    addr: String,
    card_idxs: Vec<usize>,
    role: Option<Role>,
    position: u16,
    rank: Option<u8>,
}

impl Player {
    pub fn new(addr: String, position: u16) -> Self {
        Self {
            addr,
            card_idxs: vec![],
            position,
            role: None,
            rank: None,
        }
    }
    pub fn take_card(&mut self, card_idx: usize) -> HandleResult<usize> {
        if let Some(p) = self.card_idxs.iter().position(|i| *i == card_idx) {
            Ok(self.card_idxs.remove(p))
        } else {
            Err(Error::InvalidCardIndex(card_idx))?
        }
    }
    pub fn card_idxs(&self) -> &[usize] {
        &self.card_idxs
    }
    pub fn is_attacker(&self) -> bool {
        self.role == Some(Role::Attacker)
    }
    pub fn set_role(&mut self, role: Option<Role>) {
        self.role = role;
    }
    pub fn can_attack(&self) -> bool {
        matches!(self.role, Some(Role::Attacker | Role::CoAttacker))
    }
    pub fn can_defend(&self) -> bool {
        matches!(self.role, Some(Role::Defender))
    }
    pub fn set_rank(&mut self, rank: u8) {
        self.rank = Some(rank)
    }
    pub fn rank(&self) -> Option<u8> {
        self.rank
    }
}

#[derive(BorshSerialize, BorshDeserialize)]
pub enum Attack {
    ConfirmOpen { open_idx: usize },
    Open { open: Card },
    ConfirmClose { open: Card, close_idx: usize },
    Closed { open: Card, close: Card },
}

impl Attack {
    pub fn new(card_idx: usize) -> Self {
        Self::ConfirmOpen { open_idx: card_idx }
    }

    pub fn is_open(&self) -> bool {
        matches!(self, Self::Open { .. })
    }

    pub fn is_closed(&self) -> bool {
        matches!(self, Self::Closed { .. })
    }

    pub fn is_confirmed(&self) -> bool {
        matches!(self, Self::Open { .. } | Self::Closed { .. })
    }

    pub fn confirm_open(&mut self, value: String) -> HandleResult<()> {
        match self {
            Attack::ConfirmOpen { open_idx } => {
                let open = Card::new(*open_idx, value);
                let _ = replace(self, Attack::Open { open });
            }
            _ => Err(Error::InvalidAttackStatus)?,
        }
        Ok(())
    }

    pub fn confirm_close(&mut self, value: String) -> HandleResult<()> {
        match self {
            Attack::ConfirmClose { open, close_idx } => {
                let open = open.clone();
                let close = Card::new(*close_idx, value);
                let _ = replace(self, Attack::Closed { open, close });
            }
            _ => Err(Error::InvalidAttackStatus)?,
        }
        Ok(())
    }

    pub fn close(&mut self, card: &Card) -> HandleResult<()> {
        match self {
            Attack::Open { open } => {
                let open = open.clone();
                let _ = replace(
                    self,
                    Attack::ConfirmClose {
                        open,
                        close_idx: card.idx,
                    },
                );
            }
            _ => Err(Error::InvalidAttackStatus)?,
        }
        Ok(())
    }

    pub fn can_be_closed_by(&self, card: &Card, trump: &Card) -> HandleResult<bool> {
        match self {
            Attack::Open { open } => {
                if open.is_same_suit(&trump) {
                    // Trump suit can only be closed by trump suit
                    Ok(open.is_covered_by(&card))
                } else if card.is_same_suit(&trump) {
                    // Trump suit can always close non-trump suit
                    Ok(true)
                } else {
                    Ok(open.is_covered_by(&card))
                }
            }
            _ => Err(Error::InvalidAttackStatus)?,
        }
    }

    pub fn card_refs(&self) -> Vec<&Card> {
        match self {
            Attack::ConfirmOpen { .. } => vec![],
            Attack::Open { open } => vec![&open],
            Attack::ConfirmClose { open, .. } => vec![&open],
            Attack::Closed { open, close } => vec![&open, &close],
        }
    }

    pub fn into_cards(self) -> HandleResult<Vec<Card>> {
        match self {
            Attack::Open { open } => Ok(vec![open]),
            Attack::Closed { open, close } => Ok(vec![open, close]),
            _ => Err(Error::InvalidAttackStatus)?,
        }
    }
}

impl Durak {
    /// Reset game state to prepare for next game.
    pub fn reset(&mut self, effect: &mut Effect) {
        self.random_id = 0;
        self.deck_offset = 0;
        self.stage = Stage::Waiting;
        self.players.clear();
        self.attacks.clear();
        self.trump = None;
        self.num_of_finished = 0;
        self.timeout = 0;
        effect.allow_exit(true);
    }

    /// Try start the game when there's enough players.
    pub fn try_start_game(&mut self, effect: &mut Effect) {
        if self.players.len() == self.num_of_players {
            effect.start_game();
            self.stage = Stage::Shuffling;
        }
    }

    /// Return a vector of mutable player references in acting order
    /// which starts from who has the `role`.
    pub fn players_in_acting_order_mut(&mut self, role: Role) -> HandleResult<Vec<&mut Player>> {
        let pos = self.get_player_by_role(role)?.position.clone();
        let mut players: Vec<&mut Player> = self.players.values_mut().collect();
        players.sort_by_key(|p| {
            if p.position >= pos {
                p.position
            } else {
                p.position + MAX_NUM_OF_PLAYERS as u16
            }
        });
        Ok(players)
    }

    /// Return a vector of players in rank order. Unfinished player stays at last.
    pub fn players_in_rank_order(&self) -> Vec<&Player> {
        let mut players: Vec<&Player> = self.players.values().collect();
        players.sort_by_key(|p| {
            if let Some(rank) = p.rank {
                rank
            } else {
                MAX_NUM_OF_PLAYERS as u8
            }
        });
        players
    }

    /// Return a vector of mutable player references in position order
    /// which starts from the smallest.
    pub fn players_in_position_order_mut(&mut self) -> HandleResult<Vec<&mut Player>> {
        let mut players: Vec<&mut Player> = self.players.values_mut().collect();
        players.sort_by_key(|p| p.position);
        Ok(players)
    }

    /// Initialize the roles for players.
    pub fn init_roles(&mut self) -> HandleResult<()> {
        let mut players_in_order = self.players_in_position_order_mut()?;
        players_in_order
            .get_mut(0)
            .map(|p| p.role = Some(Role::Attacker));
        players_in_order
            .get_mut(1)
            .map(|p| p.role = Some(Role::Defender));
        players_in_order
            .get_mut(2)
            .map(|p| p.role = Some(Role::CoAttacker));
        players_in_order.get_mut(3).map(|p| p.role = None);
        Ok(())
    }

    /// Rotate the roles based on the game result.  If the defender
    /// successfully defensed, he becomes the next attacker.  Otherwise
    /// the co-attacker becomes the next attacker.
    pub fn rotate_roles(&mut self, attack_succeed: bool) -> HandleResult<()> {
        let role = if attack_succeed {
            if self.num_of_players == 2 {
                Role::Attacker
            } else {
                Role::CoAttacker
            }
        } else {
            Role::Defender
        };
        let mut players_in_order: Vec<&mut Player> = self
            .players_in_acting_order_mut(role)?
            .into_iter()
            .filter(|p| p.rank().is_none())
            .collect();
        players_in_order
            .get_mut(0)
            .map(|p| p.role = Some(Role::Attacker));
        players_in_order
            .get_mut(1)
            .map(|p| p.role = Some(Role::Defender));
        players_in_order
            .get_mut(2)
            .map(|p| p.role = Some(Role::CoAttacker));
        players_in_order.get_mut(3).map(|p| p.role = None);
        Ok(())
    }

    /// Wether it is available to attack or not.
    ///
    /// The conditions to check:
    /// - Current stage is acting
    /// - There are less than 6 attacks at the moment
    /// - The defender has cards in hand
    pub fn can_attack(&self) -> HandleResult<bool> {
        Ok(matches!(self.stage, Stage::Acting | Stage::EndOfRound)
            && self.attacks.len() < MAX_ATTACK_COUNT
            && !self
                .get_player_by_role(Role::Defender)?
                .card_idxs
                .is_empty())
    }

    /// Whether it is available to defend or not.
    pub fn can_defend(&self) -> HandleResult<bool> {
        Ok(self.stage == Stage::Acting)
    }

    /// Whether the card can be used to attack or not.
    pub fn is_valid_attack_card(&self, card: &Card) -> bool {
        self.attacks
            .iter()
            .flat_map(|a| a.card_refs())
            .find(|c| c.is_same_kind(card))
            .is_some()
    }

    /// Get the player by its `role`.
    pub fn get_player_by_role(&self, role: Role) -> HandleResult<&Player> {
        Ok(self
            .players
            .values()
            .find(|p| p.role.as_ref().is_some_and(|p| p.eq(&role)))
            .ok_or(Error::NoPlayerFoundByRole(role))?)
    }

    /// Get the mut player by its `role`.
    pub fn get_player_by_role_mut(&mut self, role: Role) -> HandleResult<&mut Player> {
        Ok(self
            .players
            .values_mut()
            .find(|p| p.role.as_ref().is_some_and(|p| p.eq(&role)))
            .ok_or(Error::NoPlayerFoundByRole(role))?)
    }

    /// Return if the `card` has trump suit.
    pub fn is_trump_suit(&self, card: &Card) -> HandleResult<bool> {
        let trump = self.trump.as_ref().ok_or(Error::NoTrump)?;
        Ok(trump.is_same_suit(card))
    }

    /// Reveal the trump card.  Here we reveal the last card as trump
    pub fn reveal_trump(&mut self, effect: &mut Effect) -> HandleResult<()> {
        effect.reveal(self.random_id, vec![TRUMP_IDX]);
        self.stage = Stage::RevealingTrump;
        Ok(())
    }

    /// Update trump card.
    pub fn update_trump(&mut self, effect: &mut Effect) -> HandleResult<()> {
        let revealed = effect.get_revealed(self.random_id)?;
        let Some(trump) = revealed.get(&TRUMP_IDX) else {
            Err(Error::TrumpNotRevealed)?
        };
        self.trump = Some(Card::new(TRUMP_IDX, trump.to_owned()));
        Ok(())
    }

    /// There's no card in defender's hand and all attacks have been closed.
    pub fn is_fully_defended(&self) -> HandleResult<bool> {
        let def = self.get_player_by_role(Role::Defender)?;
        Ok(def.card_idxs().is_empty() && self.attacks.iter().all(|a| a.is_closed()))
    }

    /// Set waiting timeout for actions.  We always count 10 seconds
    /// after each action, unless there can't be any more attacking
    /// and defends.
    pub fn set_timeout_or_end_round(&mut self, effect: &mut Effect) -> HandleResult<()> {
        // Do nothing if the end is already ended
        if self.stage == Stage::EndOfGame {
            return Ok(());
        }
        if self.is_fully_defended()? {
            self.end_round(false, effect)?;
        } else {
            if self.attacks.iter().all(Attack::is_confirmed) {
                if self.stage == Stage::EndOfRound {
                    let p = self.get_player_by_role(Role::Attacker)?;
                    effect.action_timeout(&p.addr, END_OF_ROUND_TIMEOUT_MS);
                    self.timeout = effect.timestamp() + END_OF_ROUND_TIMEOUT_MS;
                } else if self.attacks.iter().any(Attack::is_open) {
                    let p = self.get_player_by_role(Role::Defender)?;
                    effect.action_timeout(&p.addr, ACT_TIMEOUT_MS);
                    self.timeout = effect.timestamp() + ACT_TIMEOUT_MS;
                } else {
                    let p = self.get_player_by_role(Role::Attacker)?;
                    effect.action_timeout(&p.addr, ACT_TIMEOUT_MS);
                    self.timeout = effect.timestamp() + ACT_TIMEOUT_MS;
                }
            }
        }
        Ok(())
    }

    pub fn remove_roles_for_escaped_players(&mut self) {
        for p in self.players.values_mut() {
            if p.rank().is_some() {
                p.set_role(None);
            }
        }
    }

    /// Settle the game result
    ///
    /// We use a simple rule to transfer tokens:
    ///     The first finished player got all tokens from the last player
    pub fn settle_game(&mut self, effect: &mut Effect) -> HandleResult<()> {
        let players = self.players_in_rank_order();
        let winner = players.first().ok_or(Error::EmptyPlayers)?;
        let loser = players.last().ok_or(Error::EmptyPlayers)?;
        effect.settle(Settle::add(&winner.addr, self.bet_amount));
        effect.settle(Settle::sub(&loser.addr, self.bet_amount));
        for p in players {
            effect.settle(Settle::eject(&p.addr));
        }
        effect.checkpoint();
        effect.wait_timeout(RESET_TIMEOUT_MS);
        Ok(())
    }

    /// End the game if there's only one player left
    pub fn maybe_end_game(&mut self, effect: &mut Effect) -> HandleResult<()> {
        if self.num_of_finished >= self.num_of_players - 1 {
            self.stage = Stage::EndOfGame;
            return self.settle_game(effect);
        }
        Ok(())
    }

    /// End the round
    pub fn end_round(&mut self, attack_succeed: bool, effect: &mut Effect) -> HandleResult<()> {
        // Do nothing if the game is already ended
        if self.stage == Stage::EndOfGame {
            return Ok(());
        }

        // As the defender gave up, the attackers can give more cards.
        if self.stage == Stage::Acting && attack_succeed && self.attacks.len() < MAX_ATTACK_COUNT {
            self.stage = Stage::EndOfRound;
            let def = self.get_player_by_role(Role::Defender)?;
            effect.action_timeout(&def.addr, END_OF_ROUND_TIMEOUT_MS);
            self.timeout = effect.timestamp() + END_OF_ROUND_TIMEOUT_MS;
            return Ok(());
        }

        // If the attack was succeed, the defender takes all cards,
        // otherwise we drop all cards
        if attack_succeed {
            let mut attacks = Vec::with_capacity(MAX_ATTACK_COUNT);
            swap(&mut attacks, &mut self.attacks);
            let mut cards: Vec<usize> = attacks
                .into_iter()
                .flat_map(Attack::into_cards)
                .flatten()
                .map(|c| c.idx)
                .collect();
            let defender = self.get_player_by_role_mut(Role::Defender)?;
            defender.card_idxs.append(&mut cards)
        } else {
            self.attacks.clear();
        }

        if self.deck_offset < DECK_LEN - 1
            && self
                .players
                .values()
                .find(|p| p.card_idxs.len() < MIN_HAND_CARD_COUNT)
                .is_some()
        {
            self.deal_cards(effect)?;
        } else {
            self.ask_to_act(effect)?;
        }
        self.rotate_roles(attack_succeed)?;
        self.remove_roles_for_escaped_players();
        Ok(())
    }

    /// Update the escaped players and maybe end the game when there's
    /// only one player left.
    pub fn update_escaped_players(&mut self) -> HandleResult<()> {
        if self.deck_offset == DECK_LEN - 1 {
            let mut num_of_finished = self.num_of_finished;
            let players = self.players_in_acting_order_mut(Role::Attacker)?;
            for p in players {
                if p.card_idxs().is_empty() && p.rank().is_none() {
                    p.set_rank(num_of_finished as u8);
                    num_of_finished += 1;
                }
            }
            self.num_of_finished = num_of_finished;
        }
        Ok(())
    }

    pub fn is_all_attacks_confirmed(&self) -> bool {
        self.attacks.iter().all(|a| a.is_confirmed())
    }

    pub fn get_attack(&self, idx: u8) -> HandleResult<&Attack> {
        Ok(self
            .attacks
            .get(idx as usize)
            .ok_or(Error::InvalidAttackIndex(idx))?)
    }

    pub fn get_attack_mut(&mut self, idx: u8) -> HandleResult<&mut Attack> {
        Ok(self
            .attacks
            .get_mut(idx as usize)
            .ok_or(Error::InvalidAttackIndex(idx))?)
    }

    pub fn get_trump(&self) -> HandleResult<&Card> {
        Ok(self.trump.as_ref().ok_or(Error::NoTrump)?)
    }

    /// Ask the players to act
    pub fn ask_to_act(&mut self, effect: &mut Effect) -> HandleResult<()> {
        self.stage = Stage::Acting;
        self.set_timeout_or_end_round(effect)?;
        Ok(())
    }

    /// Update the attack states based on the decrypted information.
    pub fn update_attacks(&mut self, effect: &mut Effect) -> HandleResult<()> {
        let revealed = effect.get_revealed(self.random_id)?;
        for attack in self.attacks.iter_mut() {
            match attack {
                Attack::ConfirmOpen { open_idx } => {
                    let value = revealed
                        .get(open_idx)
                        .ok_or(Error::UnexpectedUnrevealedCard(*open_idx as u8))?
                        .to_owned();
                    attack.confirm_open(value)?;
                }
                Attack::ConfirmClose { close_idx, .. } => {
                    let value = revealed
                        .get(close_idx)
                        .ok_or(Error::UnexpectedUnrevealedCard(*close_idx as u8))?
                        .to_owned();
                    attack.confirm_close(value)?;
                }
                _ => (),
            }
        }
        self.update_escaped_players()?;
        self.maybe_end_game(effect)?;
        self.set_timeout_or_end_round(effect)?;
        Ok(())
    }

    pub fn reveal_cards_or_update_attacks(
        &mut self,
        mut idxs: Vec<usize>,
        effect: &mut Effect,
    ) -> HandleResult<()> {
        let revealed = effect.get_revealed(self.random_id)?;
        idxs.retain(|i| !revealed.contains_key(&i));
        if idxs.is_empty() {
            self.update_attacks(effect)?;
        } else {
            effect.reveal(self.random_id, idxs);
        }
        Ok(())
    }

    /// Dealing cards by assign cards to players.
    /// Each player will receive cards until he has 6 in hand.
    /// This progress starts from the current attacker postion.
    pub fn deal_cards(&mut self, effect: &mut Effect) -> HandleResult<()> {
        let mut deck_offset = self.deck_offset;
        let random_id = self.random_id;
        let players = self.players_in_acting_order_mut(Role::Attacker)?;
        for p in players.into_iter() {
            let l = p.card_idxs.len();
            if l < MIN_HAND_CARD_COUNT {
                let cnt = MIN_HAND_CARD_COUNT - l;
                let new_offset = (deck_offset + cnt).min(DECK_LEN - 1);
                let mut assign_idxs: Vec<usize> = (deck_offset..new_offset).collect();
                effect.assign(random_id, &p.addr, assign_idxs.clone());
                p.card_idxs.append(&mut assign_idxs);
                deck_offset = new_offset;
                if deck_offset == DECK_LEN - 1 {
                    break;
                }
            }
        }
        self.deck_offset = deck_offset;
        self.stage = Stage::Dealing;
        Ok(())
    }

    pub fn handle_action(
        &mut self,
        effect: &mut Effect,
        sender: String,
        action: Action,
    ) -> HandleResult<()> {
        match action {
            Action::Attack { cards } => {
                if !self.can_attack()? {
                    Err(Error::CantAttack)?
                }
                if !(self.attacks.is_empty() || cards.iter().any(|c| self.is_valid_attack_card(c)))
                {
                    Err(Error::NotValidAttackCard)?
                }
                let att = self.get_player_by_role_mut(Role::Attacker)?;
                if att.addr.ne(&sender) {
                    Err(Error::PlayerIsNotAttacker)?
                }
                let mut idxs = vec![];
                let mut attacks = vec![];
                for c in cards.iter() {
                    let idx = att.take_card(c.idx)?;
                    idxs.push(idx);
                    attacks.push(Attack::new(idx));
                }
                self.attacks.append(&mut attacks);
                self.reveal_cards_or_update_attacks(idxs, effect)?;
            }
            Action::CoAttack { cards } => {
                if !self.can_attack()? {
                    Err(Error::CantAttack)?
                }
                if cards.iter().any(|c| self.is_valid_attack_card(c)) {
                    Err(Error::NotValidAttackCard)?
                }
                let coatt = self.get_player_by_role_mut(Role::CoAttacker)?;
                if coatt.addr.ne(&sender) {
                    Err(Error::PlayerIsNotCoAttacker)?
                }
                let mut idxs = vec![];
                let mut attacks = vec![];
                for c in cards.iter() {
                    let idx = coatt.take_card(c.idx)?;
                    idxs.push(idx);
                    attacks.push(Attack::new(idx));
                }
                self.attacks.append(&mut attacks);
                self.reveal_cards_or_update_attacks(idxs, effect)?;
            }
            Action::Defend { card, target } => {
                if !self.can_defend()? {
                    Err(Error::CantDefend)?
                }
                let def = self.get_player_by_role_mut(Role::Defender)?;
                def.take_card(card.idx)?;
                if def.addr.ne(&sender) {
                    Err(Error::PlayerIsNotDefender)?
                }
                let a = self.get_attack(target)?;
                if !a.can_be_closed_by(&card, self.get_trump()?)? {
                    Err(Error::InvalidDefendCard)?
                }
                let a = self.get_attack_mut(target)?;
                a.close(&card)?;
                self.reveal_cards_or_update_attacks(vec![card.idx], effect)?;
            }
            Action::Forward { card } => {
                // Conditions for forward
                // All attacks are open, and having same kind
                let mut kinds = vec![];
                for att in self.attacks.iter() {
                    match att {
                        Attack::Open { open } => kinds.push(open.kind()),
                        _ => return Err(Error::CantForward)?,
                    }
                }
                if !kinds.windows(2).all(|w| w[0] == w[1]) {
                    Err(Error::CantForward)?
                }
                if !self.is_valid_attack_card(&card) {
                    Err(Error::InvalidForwardCard)?
                }
                let def = self.get_player_by_role_mut(Role::Defender)?;
                let idx = def.take_card(card.idx)?;
                self.attacks.push(Attack::new(idx));

                // Forward roles, the current defender becomes attacker
                // and others take the roles by their accordingly
                self.rotate_roles(false)?;

                self.reveal_cards_or_update_attacks(vec![card.idx], effect)?;
            }
            Action::Take => {
                let def = self.get_player_by_role_mut(Role::Defender)?;
                let def_addr = def.addr.clone();
                if def_addr.ne(&sender) {
                    Err(Error::PlayerIsNotDefender)?
                }
                if !self.is_all_attacks_confirmed() {
                    Err(Error::UnconfirmedCard)?
                }
                if self.attacks.len() < MAX_ATTACK_COUNT {
                    self.stage = Stage::EndOfRound;
                    effect.action_timeout(def_addr, END_OF_ROUND_TIMEOUT_MS);
                    self.timeout = effect.timestamp() + END_OF_ROUND_TIMEOUT_MS;
                } else {
                    self.end_round(true, effect)?
                }
            }
            Action::Beated => {
                let att = self.get_player_by_role_mut(Role::Attacker)?;
                if att.addr.ne(&sender) {
                    Err(Error::PlayerIsNotAttacker)?
                }
                if !self.is_all_attacks_confirmed() {
                    Err(Error::UnconfirmedCard)?
                }
                self.end_round(false, effect)?;
            }
        };
        Ok(())
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn test_is_covered_by() {
        let c1 = Card::new(0, "h2".into());
        let c2 = Card::new(1, "ha".into());
        assert_eq!(c1.is_covered_by(&c2), true);
        let c1 = Card::new(0, "d5".into());
        let c2 = Card::new(1, "dj".into());
        assert_eq!(c1.is_covered_by(&c2), true);
        let c1 = Card::new(0, "st".into());
        let c2 = Card::new(1, "sa".into());
        assert_eq!(c1.is_covered_by(&c2), true);
    }
}
