let path = require('path');
let webpack = require('webpack');
module.exports = {
  devtool: 'source-map',
  entry: './js/index.js',
  output: {
    path: path.resolve(__dirname, '../html/js'),
    filename: 'app.js',
    library: 'app',
    libraryExport: "default",
    globalObject: 'this',
    libraryTarget: 'umd'
  },
  mode: "production",
  module: {
    rules: [{
      test: /\.js$/,
      include: [
        path.resolve(__dirname, 'src')
      ],
      exclude: /(node_modules|bower_components)/,
      loader: "babel-loader",
    }]
  },
  plugins: [
    new webpack.ProvidePlugin({
      _: ['lodash']
    })
  ],
  externals: {
    lodash: {
      commonjs: 'lodash',
      commonjs2: 'lodash',
      amd: 'lodash',
      root: '_'
    }
  }
};