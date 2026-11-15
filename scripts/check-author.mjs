// Strict rule: every commit is authored by the collaborator the issue is assigned to,
// and the gh account acting (commit/push/PR) is that same collaborator.
// Runs locally in Husky pre-commit and pre-push only.
// usage: node scripts/check-author.mjs identity        (identity about to commit)
//        node scripts/check-author.mjs range <base>    (commits in <base>..HEAD)
import { execFileSync } from 'node:child_process';

const USERS = {
  '173971250+mutiur03@users.noreply.github.com': 'Mutiur03',
  '243103502+bipro96@users.noreply.github.com': 'bipro96',
  '159434864+aavas-99@users.noreply.github.com': 'Aavas-99',
};
const run = (cmd, args) => execFileSync(cmd, args, { encoding: 'utf8' }).trim();
const fail = (msg) => { console.error(`\nAUTHOR CHECK FAILED: ${msg}\nSee "Accounts" in AGENTS.md.\n`); process.exit(1); };

const [mode, base] = process.argv.slice(2);
let emails;
if (mode === 'identity') {
  emails = [[run('git', ['var', 'GIT_AUTHOR_IDENT']).match(/<(.*)>/)[1], 'current identity']];
} else if (mode === 'range') {
  emails = run('git', ['log', '--format=%ae\t%h', `${base}..HEAD`]).split('\n').filter(Boolean)
    .map((l) => l.split('\t')).map(([e, h]) => [e, `commit ${h}`]);
} else fail('usage: identity | range <base>');

const logins = new Set();
for (const [email, what] of emails) {
  const login = USERS[email.toLowerCase()];
  if (!login) fail(`${what} has author <${email}>, not one of the three collaborator noreply emails.`);
  logins.add(login);
}

// The gh account that pushes / opens the PR (GH_TOKEN or active account) must be the author too.
const actor = run('gh', ['api', 'user', '--jq', '.login']);
for (const l of logins) if (l !== actor) fail(`commits are by ${l}, but gh is acting as ${actor}. Use GH_TOKEN=$(gh auth token -u ${l}).`);

// Branch <type>/<issue#>-<slug> links the work to an issue; its assignee must be the author.
const branch = run('git', ['branch', '--show-current']);
const issue = branch.match(/^[a-z]+\/(\d+)-/)?.[1];
if (issue) {
  const assignees = run('gh', ['issue', 'view', issue, '--repo', 'Mutiur03/CSE3220_Connecta', '--json', 'assignees', '--jq', '.assignees[].login']).split('\n').filter(Boolean);
  if (!assignees.length) fail(`issue #${issue} has no assignee.`);
  for (const l of logins) if (!assignees.includes(l)) fail(`issue #${issue} is assigned to ${assignees.join(', ')}, but commits are by ${l}.`);
}
console.log(`author check ok (${[...logins].join(', ') || 'no commits'}${issue ? `, issue #${issue}` : ''})`);
