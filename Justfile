build: build-app build-core

build-app:
    (cd app; npm run build)

release-core: build-core optimize-core

optimize-core:
    mkdir -p dist
    wasm-opt -Oz core/target/wasm32-unknown-unknown/release/durak.wasm -o dist/durak.wasm

build-core:
    (cd core; cargo build -r --target wasm32-unknown-unknown)
