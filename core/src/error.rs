use race_api::prelude::*;

use crate::misc::{Role, Stage};

#[derive(thiserror::Error, Debug)]
pub enum Error {
    #[error("Internal: Trump is none")]
    NoTrump,
    #[error("Internal: Trump is not revealed")]
    TrumpNotRevealed,
    #[error("Internal: Can not find player by role: {0:?}")]
    NoPlayerFoundByRole(Role),
    #[error("Player is not attacker")]
    PlayerIsNotAttacker,
    #[error("Player is not co-attacker")]
    PlayerIsNotCoAttacker,
    #[error("Player is not defender")]
    PlayerIsNotDefender,
    #[error("Cannot attack in this round")]
    CantAttack,
    #[error("Invalid card index: {0}")]
    InvalidCardIndex(usize),
    #[error("Internal: Invalid player of players")]
    InvalidNumOfPlayers,
    #[error("Not valid attack card")]
    NotValidAttackCard,
    #[error("Invalid attack status")]
    InvalidAttackStatus,
    #[error("Invalid stage: {0:?}")]
    InvalidStage(Stage),
    #[error("Invalid attack index, index: {0}")]
    InvalidAttackIndex(u8),
    #[error("Invalid defend card")]
    InvalidDefendCard,
    #[error("Unexpected unrevealed card, index: {0}")]
    UnexpectedUnrevealedCard(u8),
    #[error("Internal: Empty players")]
    EmptyPlayers,
    #[error("There is an unconfirmed card")]
    UnconfirmedCard,
    #[error("Cannot defend")]
    CantDefend,
    #[error("Cannot forward")]
    CantForward,
    #[error("InvalidForwardCard")]
    InvalidForwardCard,
    #[error("No attack space, space: {0}, attacks: {1}")]
    NoAttackSpace(usize, usize),
}

impl From<Error> for race_api::error::HandleError {
    fn from(value: Error) -> Self {
        HandleError::Custom(value.to_string())
    }
}
