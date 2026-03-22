# GitHub Actions for KrestyKimi

## Files

- `build-and-deploy.yml` builds the Android app, runs tests, uploads the APK artifact, and can send the APK to Telegram.

## Required GitHub Secrets

- `TELEGRAM_BOT_TOKEN`: Telegram bot token used by the `sendDocument` API call.
- `TELEGRAM_CHAT_ID`: Target chat, group, or channel ID that will receive the APK.

If either secret is missing, the workflow still builds and uploads the APK artifact, but it skips the Telegram delivery step.

## Getting a Telegram Bot Token

1. Open Telegram and start a chat with `@BotFather`.
2. Run `/newbot` and follow the prompts.
3. Copy the bot token that BotFather returns.
4. Add it to your repository secrets as `TELEGRAM_BOT_TOKEN`.

## Getting the Telegram Chat ID

1. Send at least one message to the target bot, group, or channel.
2. Open `https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getUpdates` in a browser.
3. Find the `chat` object in the JSON response and copy its `id`.
4. Add it to your repository secrets as `TELEGRAM_CHAT_ID`.

For channels, you may need to add the bot as an administrator before Telegram accepts `sendDocument`.

## Manual Trigger

1. Open the repository on GitHub.
2. Go to `Actions`.
3. Select the `Build and Deploy` workflow.
4. Click `Run workflow`.
5. Optionally set `skip_tests` to `true` for a hotfix build that bypasses `./gradlew test` and the emulator instrumentation step.

When `skip_tests=true`, the workflow can still build, upload, and deliver the APK, but it intentionally ends in a failed `unvalidated` state. This prevents a green run from ever meaning "tests were skipped".

## Triggered Branches

- `main`
- `master`
