Azure Repos REST 7.1 fixtures (azure-repos-provider task 1.1).

Copied from the official Microsoft Learn reference samples (api-version 7.1), not from a live org:
- threads.json            Pull Request Threads - List (verbatim sample: MergeAttempt, ReviewersUpdate,
                          VoteUpdate, RefUpdate, text, code thread with a deleted reply, container author)
- pullrequests-active.json    Pull Requests - Get Pull Requests, "Pull requests by repository" (verbatim)
- pullrequests-completed.json Pull Requests - Get Pull Requests, "Just completed pull requests" (verbatim)
- repositories.json       Repositories - List (verbatim)
- commits-branch.json     Commits - Get Commits, "On a branch" (first 3 of the 6 sample commits; count adjusted)

Synthesized from the documented schema (the reference page has no sample):
- pr-workitems.json       Pull Request Work Items - List (ResourceRef[])
- pr-commits.json         Pull Request Commits - Get Pull Request Commits (GitCommitRef[])

A live spike against a real organization is still pending (see tasks.md 1.1).
