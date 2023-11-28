(ns durak.constants)

(if goog.DEBUG
  (do
    (def chain :chain/facade)
    (def wallet-key :facade))
  (do
    (def chain :chain/solana-mainnet)
    (def wallet-key :solana-phantom)))

(def chain->rpc
  "The mapping from chain to RPC endpoint."
  {:chain/solana-mainnet "https://rpc.racepoker.app"
   :chain/facade         "http://localhost:12002"})

(def chain->reg-addrs
  "The mapping from chain to registration addresses."
  {:chain/facade         ["DEFAULT_REGISTRATION"]
   :chain/solana-mainnet ["8gXw3wt7qSjsagjeYvncA4LvE9ZixixFvvo35kaxwnRZ"]
   :chain/solana-local   [""]})
