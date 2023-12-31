use std::collections::BTreeMap;

use borsh::{BorshDeserialize, BorshSerialize};
use error::Error;
use misc::{Action, Attack, Card, Display, Player, Role, Stage, DECK_LEN};
use race_api::prelude::*;
use race_proc_macro::game_handler;

mod error;
mod misc;

#[derive(BorshSerialize, BorshDeserialize)]
pub struct DurakAccount {
    pub bet_amount: u64,
    pub num_of_players: u8,
}

#[derive(BorshSerialize, BorshDeserialize)]
pub struct DurakCheckpoint {}

#[game_handler]
#[derive(Default, BorshSerialize, BorshDeserialize)]
pub struct Durak {
    pub random_id: usize,
    pub deck_offset: usize,
    pub num_of_players: usize,
    pub num_of_finished: usize,
    pub stage: Stage,
    pub players: BTreeMap<String, Player>,
    pub attacks: Vec<Attack>,
    pub trump: Option<Card>,
    pub bet_amount: u64,
    pub timeout: u64,
    pub attack_space: usize,
    pub beated_addrs: Vec<String>,
    pub displays: Vec<Display>,
}

fn get_deck() -> RandomSpec {
    let options = vec![
        "sa".into(),
        "ha".into(),
        "da".into(),
        "ca".into(),
        "sk".into(),
        "hk".into(),
        "dk".into(),
        "ck".into(),
        "sq".into(),
        "hq".into(),
        "dq".into(),
        "cq".into(),
        "sj".into(),
        "hj".into(),
        "dj".into(),
        "cj".into(),
        "st".into(),
        "ht".into(),
        "dt".into(),
        "ct".into(),
        "s9".into(),
        "h9".into(),
        "d9".into(),
        "c9".into(),
        "s8".into(),
        "h8".into(),
        "d8".into(),
        "c8".into(),
        "s7".into(),
        "h7".into(),
        "d7".into(),
        "c7".into(),
        "s6".into(),
        "h6".into(),
        "d6".into(),
        "c6".into(),
    ]
    .into_iter()
    .take(DECK_LEN)
    .collect();
    RandomSpec::ShuffledList { options }
}

impl GameHandler for Durak {
    type Checkpoint = DurakCheckpoint;

    fn init_state(effect: &mut Effect, init_account: InitAccount) -> HandleResult<Self> {
        let a: DurakAccount = init_account.data()?;
        effect.allow_exit(true);
        Ok(Self {
            num_of_players: a.num_of_players as usize,
            bet_amount: a.bet_amount,
            ..Default::default()
        })
    }

    fn handle_event(&mut self, effect: &mut Effect, event: Event) -> HandleResult<()> {
        self.displays.clear();
        match event {
            Event::Custom { sender, raw } => {
                let action = Action::try_parse(&raw)?;
                self.handle_action(effect, sender, action)?;
            }
            Event::Ready => {
                self.try_start_game(effect);
            }
            Event::Sync { new_players, .. } => {
                for p in new_players.iter() {
                    self.players
                        .insert(p.addr.clone(), Player::new(p.addr.clone(), p.position));
                }
                self.try_start_game(effect);
            }
            Event::GameStart { .. } => {
                let rnd_spec = get_deck();
                effect.allow_exit(false);
                self.random_id = effect.init_random_state(rnd_spec);
                self.stage = Stage::Dealing;
            }
            // Receive when the deck shuffling is ready.
            Event::RandomnessReady { .. } => {
                self.reveal_trump(effect)?;
                self.init_roles()?;
            }
            Event::SecretsReady { .. } => {
                match &self.stage {
                    Stage::RevealingTrump => {
                        self.update_trump(effect)?;
                        self.deal_cards(effect)?;
                    }
                    Stage::Dealing => {
                        self.ask_to_act(effect)?;
                    }
                    Stage::Acting => {
                        self.update_attacks(effect)?;
                    }
                    Stage::EndOfRound => {
                        self.update_attacks(effect)?;
                    }
                    _ => {
                        return Err(Error::InvalidStage(self.stage))?;
                    }
                };
            }
            // Player can only leave before the game starts.
            Event::Leave { player_addr } => {
                self.players.remove(&player_addr);
                effect.settle(Settle::eject(&player_addr));
                effect.checkpoint();
            }
            Event::ActionTimeout { .. } => {
                if self.stage == Stage::Acting {
                    if self.attacks.is_empty() || self.attacks.iter().all(Attack::is_closed) {
                        let att = self.get_player_by_role(Role::Attacker)?;
                        self.displays.push(Display::PlayerAction {
                            addr: att.addr(),
                            action: Action::Beated,
                        });
                        self.end_round(false, effect)?;
                    } else {
                        let def = self.get_player_by_role(Role::Defender)?;
                        self.displays.push(Display::PlayerAction {
                            addr: def.addr(),
                            action: Action::Take,
                        });
                        self.end_round(true, effect)?;
                    }
                } else if self.stage == Stage::EndOfRound {
                    self.end_round(true, effect)?;
                } else {
                    return Err(Error::InvalidStage(self.stage))?;
                }
            }
            Event::WaitingTimeout => {
                self.reset(effect);
            }
            _ => (),
        }
        Ok(())
    }

    fn into_checkpoint(self) -> HandleResult<Self::Checkpoint> {
        Ok(DurakCheckpoint {})
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use race_test::prelude::*;
}
