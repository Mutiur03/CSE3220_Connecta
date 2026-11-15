// Strict rule: every commit is authored AND committed by the collaborator the issue is assigned to,
// and the gh account acting (commit/push/PR) is that same collaborator.
// Runs locally in Husky pre-commit and pre-push only.
// usage: node scripts/check-author.mjs identity        (identity about to commit)
//        node scripts/check-author.mjs range <base>    (commits in <base>..HEAD)
import { execFileSync } from 'node:child_process';

// Each login has the email GitHub itself uses for that account (so local and GitHub-made commits
// match) and the old noreply address, which stays valid for commits made before the switch.
const USERS = {
  'mutiur5bb@gmail.com': 'Mutiur03',
  '173971250+mutiur03@users.noreply.github.com': 'Mutiur03',
  'bb123biprobiswas@gmail.com': 'bipro96',
  '243103502+bipro96@users.noreply.github.com': 'bipro96',
  'roy.aavas@gmail.com': 'Aavas-99',
  '159434864+aavas-99@users.noreply.github.com': 'Aavas-99',
};
const run = (cmd, args) => execFileSync(cmd, args, { encoding: 'utf8' }).trim();
const fail = (msg) => { console.error(`\nAUTHOR CHECK FAILED: ${msg}\nSee "Accounts" in AGENTS.md.\n`); process.exit(1); };
const emailOf = (ident) => ident.match(/<(.*)>/)[1];

const [mode, base] = process.argv.slice(2);
let commits; // [{ what, author, committer }]
if (mode === 'identity') {
  commits = [{
    what: 'current identity',
    author: emailOf(run('git', ['var', 'GIT_AUTHOR_IDENT'])),
    committer: emailOf(run('git', ['var', 'GIT_COMMITTER_IDENT'])),
  }];
} else if (mode === 'range') {
  commits = run('git', ['log', '--format=%ae\t%ce\t%h', `${base}..HEAD`]).split('\n').filter(Boolean)
    .map((l) => l.split('\t')).map(([author, committer, hash]) => ({ what: `commit ${hash}`, author, committer }));
} else fail('usage: identity | range <base>');

const loginOf = (email, what, role) => {
  const login = USERS[email.toLowerCase()];
  if (!login) fail(`${what} has ${role} <${email}>, which is not a collaborator's email.`);
  return login;
};

const logins = new Set();
for (const { what, author, committer } of commits) {
  const authorLogin = loginOf(author, what, 'author');
  const committerLogin = loginOf(committer, what, 'committer');
  if (authorLogin !== committerLogin) {
    fail(`${what} is authored by ${authorLogin} but committed by ${committerLogin} (<${committer}>). `
      + 'Run `source scripts/use-account.sh <login>` so rebases and amends use the same identity.');
  }
  logins.add(authorLogin);
}

// The gh account that pushes / opens the PR (GH_TOKEN or active account) must be the author too.
const actor = run('gh', ['api', 'user', '--jq', '.login']);
for (const l of logins) if (l !== actor) fail(`commits are by ${l}, but gh is acting as ${actor}. Run \`source scripts/use-account.sh ${l}\`.`);

// Branch <type>/<issue#>-<slug> links the work to an issue; its assignee must be the author.
const branch = run('git', ['branch', '--show-current']);
const issue = branch.match(/^[a-z]+\/(\d+)-/)?.[1];
if (issue) {
  const assignees = run('gh', ['issue', 'view', issue, '--repo', 'Mutiur03/CSE3220_Connecta', '--json', 'assignees', '--jq', '.assignees[].login']).split('\n').filter(Boolean);
  if (!assignees.length) fail(`issue #${issue} has no assignee.`);
  for (const l of logins) if (!assignees.includes(l)) fail(`issue #${issue} is assigned to ${assignees.join(', ')}, but commits are by ${l}.`);
}
console.log(`author check ok (${[...logins].join(', ') || 'no commits'}${issue ? `, issue #${issue}` : ''})`);
