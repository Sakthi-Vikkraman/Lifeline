# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Status

The repository is empty: no code, build tooling, or tests exist yet. Once a stack is chosen, add the build/run/test commands here, and replace this section with an architecture overview.

## Product spec: Lifeline

Lifeline is an app that helps the user keep their daily timeline on track by asking them every hour what they're doing.

- **Hourly check-in:** an alarm/notification fires every hour.
- **Dismissal requires proof:** the alarm/notification only clears after the user submits an entry, either a photo of what they're working on or a typed description of the work.
- **Tagging:** every entry is tagged **productivity** or **entertainment**.
- **Dashboard tab:** shows the current day split by hour, with the work logged in each hour.
- **Calendar tab:** each day's color reflects how many entries were logged and the productivity/entertainment mix from their tags.
- **Theme:** bold, confident and daring, with a gym/fitness feel. The app is meant for the user's boyfriend.
