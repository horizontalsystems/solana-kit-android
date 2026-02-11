# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Pure Kotlin Solana SDK for Android. Syncs Solana wallet data (SOL balance, SPL token accounts, transactions) and provides structured, reactive data via Kotlin StateFlow/Flow. Based on metaplex-foundation/SolanaKT. Published as `com.github.horizontalsystems:solana-kit-android` via JitPack.

## Build Commands

```bash
./gradlew build                      # Build all modules
./gradlew :solanakit:build           # Build library only
./gradlew :app:build                 # Build sample app
./gradlew test                       # Run unit tests
./gradlew :solanakit:test            # Run library unit tests only
./gradlew connectedAndroidTest       # Run instrumented tests (requires device/emulator)
./gradlew clean                      # Clean build artifacts
```

## Modules

- **solanakit/** — The library. Android library module (`com.android.library`). Min SDK 26, Target/Compile SDK 35, Java 17.
- **app/** — Sample application demonstrating library usage. Application ID: `io.horizontalsystems.solanakit.sample`.

## Architecture

### Entry Point

`SolanaKit` (`solanakit/.../SolanaKit.kt`) is the main public API. Instantiated via `SolanaKit.getInstance()` factory method which manually wires all dependencies. Exposes reactive StateFlow/Flow properties for balance, token accounts, transactions, and sync states. Lifecycle: `start()` / `stop()` / `pause()` / `resume()` / `refresh()`.

### Core Layer (`core/`)

- **SyncManager** — Orchestrates all sync operations. Delegates to BalanceManager, TokenAccountManager, and TransactionSyncer. Manages `SyncState` (Synced/Syncing/NotSynced sealed class) for each domain.
- **BalanceManager** — Fetches SOL balance from RPC, caches in storage.
- **TokenAccountManager** — Manages SPL token accounts. Syncs token metadata from SolanaFM API.
- **ConnectionManager** — Monitors Android network connectivity, triggers sync state changes.

### Transaction Layer (`transactions/`)

- **TransactionManager** — Transaction creation (SOL and SPL transfers), filtering, and reactive emission.
- **TransactionSyncer** — Syncs transaction history from chain.
- **PendingTransactionSyncer** — Monitors pending transactions for confirmation.
- **getTransaction.kt** — RPC endpoint extension for fetching full transaction details via Alchemy.
- **SolanaFmService** — REST client for SolanaFM API (token account metadata).
- **ComputeBudgetProgram** — Priority fee instruction generation.

### RPC Layer (`noderpc/`)

- **ApiSyncer** — Timer-based periodic block height sync via RPC. Handles pause/resume.
- **NftClient** — NFT metadata fetching.

### Database Layer (`database/`)

Two separate Room databases, both using `fallbackToDestructiveMigration()`:
- **MainDatabase** — BalanceEntity, LastBlockHeightEntity, InitialSyncEntity.
- **TransactionDatabase** — Transaction, TokenTransfer, TokenAccount, MintAccount, LastSyncedTransaction.

Storage classes (MainStorage, TransactionStorage) abstract DAO access.

### Communication Pattern

Components communicate via listener interfaces (ISyncListener, IApiSyncerListener, IBalanceListener, ITokenAccountListener). SolanaKit implements ISyncListener to bridge internal events to public StateFlow emissions.

## Key Dependencies

- **sol4k** (`0.5.16`) — Pure Kotlin Solana library (signing, connection, transactions)
- **SolanaKT / metaplex-android** — Metaplex SDK for RPC and token metadata
- **Room** (`2.7.2`) — Local database persistence (uses KAPT for annotation processing)
- **hd-wallet-kit-android** — BIP32/BIP44 key derivation (used in `Signer.kt`)
- **OkHttp** (`5.0.0-alpha.10`) + **Retrofit** (`2.9.0`) — Networking
- **Moshi** + **Gson** — JSON serialization (both are used in different parts)
- Bouncy Castle dependency substitution: `bcprov-jdk15to18:1.68` → `bcprov-jdk15on:1.65`

## Package Structure

All library code lives under `io.horizontalsystems.solanakit`. Key sub-packages: `core`, `database.main`, `database.transaction`, `models`, `network`, `noderpc`, `transactions`.