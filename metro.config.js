const { getDefaultConfig } = require("expo/metro-config");
const { withNativeWind } = require("nativewind/metro");
const path = require("path");

const config = getDefaultConfig(__dirname);

// Ensure Metro watches the local native module source
config.watchFolders = [
  ...(config.watchFolders || []),
  path.resolve(__dirname, "modules"),
];

module.exports = withNativeWind(config, {
  input: "./global.css",
  forceWriteFileSystem: true,
});
