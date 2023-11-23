use std::collections::BTreeMap;

use borsh::{BorshDeserialize, BorshSerialize};
use error::Error;
use misc::{Action, Attack, Card, Player, Stage};
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
}

fn get_deck() -> RandomSpec {
    RandomSpec::ShuffledList {
        options: vec![
            "ha".into(),
            "h6".into(),
            "h7".into(),
            "h8".into(),
            "h9".into(),
            "ht".into(),
            "hj".into(),
            "hq".into(),
            "hk".into(),
            "sa".into(),
            "s6".into(),
            "s7".into(),
            "s8".into(),
            "s9".into(),
            "st".into(),
            "sj".into(),
            "sq".into(),
            "sk".into(),
            "da".into(),
            "d6".into(),
            "d7".into(),
            "d8".into(),
            "d9".into(),
            "dt".into(),
            "dj".into(),
            "dq".into(),
            "dk".into(),
            "ca".into(),
            "c6".into(),
            "c7".into(),
            "c8".into(),
            "c9".into(),
            "ct".into(),
            "cj".into(),
            "cq".into(),
            "ck".into(),
        ],
    }
}

impl GameHandler for Durak {
    type Checkpoint = DurakCheckpoint;

    fn init_state(_effect: &mut Effect, init_account: InitAccount) -> HandleResult<Self> {
        let a: DurakAccount = init_account.data()?;
        Ok(Self {
            num_of_players: a.num_of_players as usize,
            bet_amount: a.bet_amount,
            ..Default::default()
        })
    }

    fn handle_event(&mut self, effect: &mut Effect, event: Event) -> HandleResult<()> {
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
            }
            Event::ActionTimeout { player_addr } => {
                if self.stage == Stage::Acting {
                    let p = self
                        .players
                        .get(&player_addr)
                        .ok_or(HandleError::InvalidPlayer)?;
                    self.end_round(!p.is_attacker(), effect)?;
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
