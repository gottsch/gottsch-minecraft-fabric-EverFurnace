# Changelog for EverFurnace 1.21.4

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2024-12-16

### Changed

- Save any unused detla/elapsed time (remainingDeltaTime) to the next tick
    in order to process against any inputted cook items, ie provided by hopper.
    if none is found then remainingDeltaTime is reset to 0.
- Refactored to use Accessors and Invokers instead of accesstransformer.cfg
- Refactored to use mixin best practices to rename properties. this can cause a 
    one-time loss of elapsed time on first load of furnace.

### Added

- Saving new data -> remainingDeltaTime

## [1.0.1] - 2024-12-13

### Changed

- Fixed the cookingProgress time after the elapsed time is applied.

## [1.0.0] - 2024-12-09
- Initial release.