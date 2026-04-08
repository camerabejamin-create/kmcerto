module.exports = function (api) {
  api.cache(true);
  const plugins = [];

    // react-native-reanimated/plugin MUST be listed last
  plugins.push("react-native-reanimated/plugin");

  return {
    presets: [["babel-preset-expo", { jsxImportSource: "nativewind" }], "nativewind/babel"],
    plugins,
  };
};
