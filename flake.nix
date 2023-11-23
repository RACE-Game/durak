{
  description = "A Durak poker game on Solana";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    rust-overlay.url = "github:oxalica/rust-overlay";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, rust-overlay }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        overlays = [ (import rust-overlay) ];
        pkgs = import nixpkgs { inherit system overlays; };
      in
        {
          devShell = pkgs.mkShell {
            buildInputs = with pkgs; [
              (rust-bin.stable.latest.default.override {
                extensions = [ "rust-src" ];
                targets = [ "wasm32-unknown-unknown" ];
              })
              binaryen
              cargo
              jdk17_headless
              nodejs_18
              clojure
              clojure-lsp
              just
              openssl
              pkg-config
            ];
          };
        }
    );

  nixConfig = {
    bash-prompt-prefix = "[soldurak]";
  };
}
