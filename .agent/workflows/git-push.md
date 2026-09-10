---
description: git push procedure for this project
---

This project uses HTTPS for Git operations because SSH is not configured on the environment.

// turbo
1. Check the current remote URL
```bash
git remote -v
```

2. If it's using SSH (`git@github.com...`), change it to HTTPS:
```bash
git remote set-url origin https://github.com/tanminkwan/mwagent.git
```

3. Push the changes
```bash
git push
```
