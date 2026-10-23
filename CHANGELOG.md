# Changelog

Versioning is semantic (major.minor.patch): patch for small fixes, minor for new features, major only for very large changes, rewrites or database breaks. Each release adds a section here.

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
