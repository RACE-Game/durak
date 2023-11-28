(ns durak.types
  (:require [borsh.macros :as m]))

(m/defstruct DurakAccount
  [^:u64 bet
   ^:u8 size])

(defprotocol ICard
  (suit [x])
  (kind [x]))

(m/defstruct Card
  [^:usize idx
   ^:string value])

(extend-protocol ICard
  Card
  (suit [c] (subs (:value c) 0 1))
  (kind [c] (subs (:value c) 1 2))
  string
  (suit [s] (subs s 0 1))
  (kind [s] (subs s 1 2)))

(def roles [:role/attacker :role/defender :role/co-attacker])

(def stages [:stage/waiting
             :stage/shuffling
             :stage/revealing-trump
             :stage/dealing
             :stage/acting
             :stage/end-of-round
             :stage/end-of-game])

(defprotocol IAction
  (action-type [x]))

(m/defstruct Attack [^{:vec {:struct Card}} cards])
(m/defstruct CoAttack [^{:vec {:struct Card}} cards])
(m/defstruct Defend [^{:struct Card} card
                     ^:u8 target])
(m/defstruct Forward [^{:struct Card} card])
(m/defstruct Take [])
(m/defstruct Beated [])

(extend-protocol IAction
  Attack
  (action-type [_] :action/attack)
  CoAttack
  (action-type [_] :action/co-attack)
  Defend
  (action-type [_] :action/defend)
  Forward
  (action-type [_] :action/forward)
  Take
  (action-type [_] :action/take)
  Beated
  (action-type [_] :action/beated))

(m/defvariants Action [Attack CoAttack Defend Forward Take Beated])

(m/defstruct GameEvent
  [^{:enum Action} action])

(m/defstruct Player
  [^:string addr
   ^{:vec :usize} card-idxs
   ^{:option {:enum roles}} role
   ^:u16 position
   ^{:option :u8} rank])

(defprotocol IAttackType
  (attack-type [x]))

(m/defstruct ConfirmOpen [^:usize open-idx])
(m/defstruct Open [^{:struct Card} open])
(m/defstruct ConfirmClose [^{:struct Card} open ^:usize close-idx])
(m/defstruct Closed [^{:struct Card} open ^{:struct Card} close])

(extend-protocol IAttackType
  ConfirmOpen
  (attack-type [_] :attack/confirm-open)
  Open
  (attack-type [_] :attack/open)
  ConfirmClose
  (attack-type [_] :attack/confirm-close)
  Closed
  (attack-type [_] :attack/closed))

(m/defvariants AttackItem [ConfirmOpen Open ConfirmClose Closed])

(defprotocol IDisplay
  (display-type [_]))

(m/defstruct DealCards
  [^:string addr
   ^{:vec :usize} card-idxs])

(m/defstruct PlayerAction
  [^:string addr
   ^{:enum Action} action])

(extend-protocol IDisplay
  DealCards
  (display-type [_] :display/deal-cards)
  PlayerAction
  (display-type [_] :display/player-action))

(m/defvariants Display [DealCards PlayerAction])

(m/defstruct DurakState
  [^:usize random-id
   ^:usize deck-offset
   ^:usize num-of-players
   ^:usize num-of-finished
   ^{:enum stages} stage
   ^{:map [:string {:struct Player}]} players
   ^{:vec {:enum AttackItem}} attacks
   ^{:option {:struct Card}} trump
   ^:u64 bet-amount
   ^:u64 timeout
   ^:usize attack-space
   ^{:vec :string} beated
   ^{:vec {:enum Display}} displays])
