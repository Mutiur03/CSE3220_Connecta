export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'scope-enum': [2, 'always', ['frontend', 'backend', 'db', 'ci', 'docs']],
  },
};
