# Releasing the Android App

This document describes how to publish a signed release of the Budget Android app to both **GitHub Releases** and **Google Play Internal Testing**.

## Overview

Releases are fully automated via the [`release-android.yml`](../.github/workflows/release-android.yml) GitHub Actions workflow.  Pushing a version tag (e.g. `v1.2.3`) triggers the workflow, which:

1. Builds a signed release **AAB** (`bundleRelease`) and a signed release **APK** (`assembleRelease`).
2. Creates a GitHub Release for the tag and attaches both artifacts.
3. Uploads the signed AAB to the **Internal Testing** track on Google Play.

Google Play authentication uses **Workload Identity Federation (WIF)**, which does not require storing a long-lived service account JSON key as a secret.

---

## Required GitHub Secrets

All secrets must be added in the repository's **Settings → Secrets and variables → Actions**.

| Secret name | Description |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded contents of the Android release keystore (`.jks` / `.keystore` file). |
| `ANDROID_KEYSTORE_PASSWORD` | Password for the keystore file. |
| `ANDROID_KEY_ALIAS` | Alias of the signing key inside the keystore. |
| `ANDROID_KEY_PASSWORD` | Password for the signing key. |
| `GCP_WIF_PROVIDER` | Full resource name of the Workload Identity Provider (see setup below). |
| `GCP_SERVICE_ACCOUNT` | Email address of the Google Cloud service account used to publish to Google Play. |

---

## One-time Setup

### 1. Generate (or export) an Android release keystore

If you do not already have a release keystore:

```bash
keytool -genkeypair \
  -keystore release.jks \
  -alias my-key-alias \
  -keyalg RSA -keysize 2048 -validity 10000
```

Keep this file **secret and backed up**. Losing it means you cannot update the app on Google Play.

### 2. Base64-encode the keystore and save as a secret

```bash
base64 -w 0 release.jks   # Linux
base64 release.jks         # macOS
```

Paste the output as the `ANDROID_KEYSTORE_BASE64` secret.

### 3. Set up Google Play API access and a service account

Google Play publishing requires a service account that has been granted access in **Google Play Console**.

#### a. Enable the Google Play Android Developer API

1. Go to the [Google Cloud Console](https://console.cloud.google.com/) and select (or create) a project.
2. Open **APIs & Services → Library**, search for **Google Play Android Developer API**, and click **Enable**.

#### b. Create a service account

1. In the same project, go to **IAM & Admin → Service Accounts**.
2. Click **Create Service Account**, give it a name (e.g. `github-play-publisher`), and click **Create and Continue**.
3. Skip optional role/user access steps on this screen — permissions are granted in Play Console, not here.
4. Click **Done**. Note the service account's email address (e.g. `github-play-publisher@my-project.iam.gserviceaccount.com`). Save this as the `GCP_SERVICE_ACCOUNT` secret.

#### c. Grant the service account access in Google Play Console

1. Open [Google Play Console](https://play.google.com/console) → **Users and permissions**.
2. Click **Invite new users**, enter the service account email from step b, and assign the **Release manager** permission (or a custom permission set that includes *Manage production releases* / *Manage testing track releases*).
3. Click **Send invitation** → **Apply**.

### 4. Set up Workload Identity Federation (WIF)

WIF lets GitHub Actions authenticate as the service account without storing a long-lived JSON key.

#### a. Create a Workload Identity Pool

```bash
gcloud iam workload-identity-pools create "github-pool" \
  --project="PROJECT_ID" \
  --location="global" \
  --display-name="GitHub Actions pool"
```

#### b. Create a Workload Identity Provider in that pool

```bash
gcloud iam workload-identity-pools providers create-oidc "github-provider" \
  --project="PROJECT_ID" \
  --location="global" \
  --workload-identity-pool="github-pool" \
  --display-name="GitHub provider" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.actor=assertion.actor" \
  --attribute-condition="attribute.repository == 'REPO_NAME'" \
  --issuer-uri="https://token.actions.githubusercontent.com"
```

#### c. Allow the GitHub repository to impersonate the service account

```bash
gcloud iam service-accounts add-iam-policy-binding \
  "github-play-publisher@PROJECT_ID.iam.gserviceaccount.com" \
  --project="PROJECT_ID" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/attribute.repository/stevenstetzler/budget"
```

Replace `PROJECT_ID` with your Google Cloud project ID and `PROJECT_NUMBER` with its numeric project number (visible on the project dashboard).

#### d. Save the provider resource name as a secret

```bash
gcloud iam workload-identity-pools providers describe "github-provider" \
  --project="PROJECT_ID" \
  --location="global" \
  --workload-identity-pool="github-pool" \
  --format="value(name)"
```

The output looks like:
```
projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/providers/github-provider
```

Save this as the `GCP_WIF_PROVIDER` secret.

### 5. Ensure the app is already created in Google Play Console

The workflow uploads to an **existing** app listing (`com.vidalabs.budget`). The app must have at least one previous upload (even a draft) before automated uploads work.

---

## Triggering a Release

1. Commit and push all changes to `main` (or the desired branch).
2. Create and push an annotated version tag:

```bash
git tag -a v1.2.3 -m "Release v1.2.3"
git push origin v1.2.3
```

The workflow starts automatically. Monitor progress in the **Actions** tab of the repository.

---

## Artifact Paths

| Artifact | Path |
|---|---|
| Release AAB | `app/build/outputs/bundle/release/*.aab` |
| Release APK | `app/build/outputs/apk/release/*.apk` |

---

## Local Signing (Optional)

The signing config in `app/build.gradle.kts` reads credentials from environment variables at build time.  These variables are only required when you want a signed build; debug builds are unaffected.

To build a signed release locally:

```bash
export ANDROID_KEYSTORE_PATH=/path/to/release.jks
export ANDROID_KEYSTORE_PASSWORD=your_keystore_password
export ANDROID_KEY_ALIAS=your_key_alias
export ANDROID_KEY_PASSWORD=your_key_password

./gradlew bundleRelease assembleRelease
```

