# Changelog

Versioning is semantic (major.minor.patch): patch for small fixes, minor for new features, major only for very large changes, rewrites or database breaks. Each release adds a section here.

## 1.2.0
- Samba playback keeps the previous, current and next track in memory (files up to 100 MB; switch in Settings > Audio). Seeking, track changes and Previous no longer touch the network, so a sleeping NAS disk or a dropped connection can't delay them. The next track is fetched while the current one plays.
- Opening a file on a dead connection gives up after 5 s and reconnects, instead of waiting out the long network timeout.
- Albums and Library dialogs: search and source chips share one compact line, the shuffle actions moved into the dialog header, so far more results fit on a landscape screen.
- Leaving a folder returns to the same spot in the album list; Back steps out of a folder before closing the dialog.
- Disc folders show their album ("Abbey Road • CD1" instead of "CD1"), sort next to each other, and are found by searching the album or artist name.

## 1.1.0
- Several Samba shares: add, name, rescan and remove shares in Settings > Library. Each can be merged into the main library or kept separate (e.g. "Review", "Comedy").
- Library and Albums get source chips; with a source selected, *Shuffle <name>* plays just that source. Randomize and the default lists only use merged sources.
- Device music can be left out of the main library as well.
- Folder picker for the Subfolder field: browse the share instead of typing the path.
- Loudness levelling (Settings > Audio): ReplayGain/R128 tags, measured loudness for untagged tracks, background scan, level offset. Off by default.

## 1.0.1
- Standby clock moves to a new spot every minute, fading out at the end of the old minute and in at the start of the new one (slower fade, always fully on screen).
- Removed the "Standby" hint text from the standby screen.
- Header: the time is now centred; the Sleep button sits to its left, seconds and date to its right.

## 1.0.0
- First release: SMB2/3 and local music, sleep timer with fade-out, auto-dim standby clock, Audiobookshelf last-played widget, default launcher mode, four themes, landscape and portrait.
