export default {
  extends: ['@commitlint/config-conventional'],
  plugins: [
    {
      rules: {
        // Clean messages only: no Co-authored-by, no tool/AI attribution lines.
        'no-attribution': ({ raw }) => [
          !/^\s*co-authored-by:|generated (with|by)|🤖/im.test(raw),
          'commit message must not contain Co-authored-by or attribution lines',
        ],
      },
    },
  ],
  rules: {
    'scope-enum': [2, 'always', ['frontend', 'backend', 'db', 'ci', 'docs']],
    'no-attribution': [2, 'always'],
  },
};
