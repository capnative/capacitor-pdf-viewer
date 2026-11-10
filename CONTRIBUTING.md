# Contributing

This guide provides instructions for contributing to this Capacitor plugin.

## Developing

### Local Setup

1. Fork and clone the repo.
1. Install the dependencies.

    ```shell
    npm install
    ```

1. Install SwiftLint if you're on macOS. `npm run lint` also runs SwiftLint through `node-swiftlint`.

    ```shell
    brew install swiftlint
    ```

### Scripts

#### `npm run build`

Build the plugin package and generate plugin API documentation using [`@capacitor/docgen`](https://github.com/ionic-team/capacitor-docgen).

It compiles the TypeScript code from `src/` into ESM JavaScript in `dist/esm/`. Apps with bundlers import these files.

Then Rollup bundles the code into `dist/plugin.js` and `dist/plugin.cjs.js`.

#### `npm run verify`

Build the plugin package and validate the iOS and Android projects.

This is useful to run in CI to verify that the plugin builds for both platforms.

`verify:ios` needs Xcode on macOS. `verify:android` needs a Java 21 JDK and Android SDK 36.

#### `npm test`

Run the option-parsing unit tests with Vitest.

#### `npm run lint` / `npm run fmt`

Check formatting and code quality, autoformat/autofix if possible.

This template is integrated with ESLint, Prettier, and SwiftLint. Using these tools is completely optional, but the [Capacitor Community](https://github.com/capacitor-community/) strives to have consistent code style and structure for easier cooperation.

## Publishing

There is a `prepublishOnly` hook in `package.json` which prepares the plugin before publishing, so all you need to do is run:

```shell
npm publish
```

> **Note**: The [`files`](https://docs.npmjs.com/cli/v7/configuring-npm/package-json#files) array in `package.json` specifies which files get published. If you rename files/directories or add files elsewhere, you may need to update it.
