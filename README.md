# minecraft-server

Own project. Forks it depends on live in `vendor/` and are listed in `forks.lock`.

## Add a fork as a dependency

```bash
fork-repo owner/repo          # one-time: fork + clone into ~/code/forks
use-fork owner/repo           # pin YOUR fork as vendor/<repo>
```

Work happens in `~/code/forks/<repo>` (origin = your fork, upstream = original).
This repo pins a commit of that fork via submodule.
