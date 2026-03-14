# CI/CD Integration Guide

This guide explains how to integrate Factstore into your CI/CD pipeline to automatically create compliance trails and record attestations as part of your build process.

## The `X-Factstore-CI-Context` Header

When creating a trail, include the `X-Factstore-CI-Context` request header to tell the Factstore server which CI environment you are running in. The server will read well-known environment variables from its own process (when deployed inside the same CI environment) and automatically populate any missing trail fields.

**Supported values:**

| Header value | CI system |
|---|---|
| `github-actions` | GitHub Actions |
| `gitlab-ci` | GitLab CI/CD |
| `jenkins` | Jenkins |
| `circleci` | CircleCI |
| `azure-devops` | Azure DevOps Pipelines |

**Auto-populated fields:**

| Field | GitHub Actions | GitLab CI | Jenkins | CircleCI | Azure DevOps |
|---|---|---|---|---|---|
| `gitCommitSha` | `GITHUB_SHA` | `CI_COMMIT_SHA` | `GIT_COMMIT` | `CIRCLE_SHA1` | `BUILD_SOURCEVERSION` |
| `gitBranch` | `GITHUB_REF_NAME` | `CI_COMMIT_REF_NAME` | `GIT_BRANCH` | `CIRCLE_BRANCH` | `BUILD_SOURCEBRANCH` |
| `buildUrl` | constructed from `GITHUB_SERVER_URL` + `GITHUB_REPOSITORY` + `GITHUB_RUN_ID` | `CI_JOB_URL` | `BUILD_URL` | `CIRCLE_BUILD_URL` | constructed from `SYSTEM_TEAMFOUNDATIONCOLLECTIONURI` + `SYSTEM_TEAMPROJECT` + `BUILD_BUILDID` |

Fields explicitly provided in the request body always take precedence over auto-populated values.

---

## GitHub Actions

### Using `curl` directly

```yaml
name: Compliance Trail

on: [push]

jobs:
  factstore:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Create Factstore trail
        run: |
          curl -s -X POST "${{ vars.FACTSTORE_BASE_URL }}/api/v1/trails" \
            -H "Content-Type: application/json" \
            -H "X-Factstore-CI-Context: github-actions" \
            -d '{
              "flowId": "${{ vars.FACTSTORE_FLOW_ID }}",
              "gitAuthor": "${{ github.actor }}",
              "gitAuthorEmail": "${{ github.actor }}@users.noreply.github.com"
            }'
```

Because the header `X-Factstore-CI-Context: github-actions` is present, the server reads `GITHUB_SHA`, `GITHUB_REF_NAME`, and constructs the build URL from `GITHUB_SERVER_URL`/`GITHUB_REPOSITORY`/`GITHUB_RUN_ID` automatically.

### Using the `setup-factstore` action

The `actions/setup-factstore` action (defined in this repository at [`actions/setup-factstore/action.yml`](../actions/setup-factstore/action.yml)) configures the Factstore CLI for use in subsequent steps.

```yaml
name: Compliance Trail

on: [push]

jobs:
  factstore:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Factstore
        uses: ./actions/setup-factstore
        with:
          base-url: ${{ vars.FACTSTORE_BASE_URL }}
          api-token: ${{ secrets.FACTSTORE_API_TOKEN }}

      - name: Create trail
        run: |
          # CLI usage will be available once issue #33 is delivered.
          # For now, use curl with the X-Factstore-CI-Context header (see above).
          echo "Trail created for commit $GITHUB_SHA on branch $GITHUB_REF_NAME"
```

---

## GitLab CI/CD

Add a job to your `.gitlab-ci.yml` that uses `curl` with the `X-Factstore-CI-Context: gitlab-ci` header. GitLab exposes `CI_COMMIT_SHA`, `CI_COMMIT_REF_NAME`, and `CI_JOB_URL` automatically.

```yaml
factstore-trail:
  stage: .pre
  image: curlimages/curl:latest
  script:
    - |
      curl -s -X POST "$FACTSTORE_BASE_URL/api/v1/trails" \
        -H "Content-Type: application/json" \
        -H "X-Factstore-CI-Context: gitlab-ci" \
        -d "{
          \"flowId\": \"$FACTSTORE_FLOW_ID\",
          \"gitAuthor\": \"$GITLAB_USER_LOGIN\",
          \"gitAuthorEmail\": \"$GITLAB_USER_EMAIL\"
        }"
  variables:
    FACTSTORE_BASE_URL: https://factstore.example.com
    FACTSTORE_FLOW_ID: your-flow-uuid-here
```

Set `FACTSTORE_BASE_URL` and `FACTSTORE_FLOW_ID` as CI/CD variables in your GitLab project settings. Store any API token as a masked variable.

---

## Jenkins

Add a `sh` step in your `Jenkinsfile`. Jenkins populates `GIT_COMMIT`, `GIT_BRANCH`, and `BUILD_URL` in the build environment.

```groovy
pipeline {
    agent any

    environment {
        FACTSTORE_BASE_URL = 'https://factstore.example.com'
        FACTSTORE_FLOW_ID  = 'your-flow-uuid-here'
    }

    stages {
        stage('Factstore Trail') {
            steps {
                sh '''
                    curl -s -X POST "$FACTSTORE_BASE_URL/api/v1/trails" \
                      -H "Content-Type: application/json" \
                      -H "X-Factstore-CI-Context: jenkins" \
                      -d "{
                        \\"flowId\\": \\"$FACTSTORE_FLOW_ID\\",
                        \\"gitAuthor\\": \\"$BUILD_USER\\",
                        \\"gitAuthorEmail\\": \\"$BUILD_USER_EMAIL\\"
                      }"
                '''
            }
        }
    }
}
```

Install the [Build User Vars Plugin](https://plugins.jenkins.io/build-user-vars-plugin/) to expose `BUILD_USER` and `BUILD_USER_EMAIL`.

---

## CircleCI

Add a run step to your `.circleci/config.yml`. CircleCI exposes `CIRCLE_SHA1`, `CIRCLE_BRANCH`, and `CIRCLE_BUILD_URL`.

```yaml
version: 2.1

jobs:
  factstore-trail:
    docker:
      - image: cimg/base:stable
    steps:
      - run:
          name: Create Factstore trail
          command: |
            curl -s -X POST "$FACTSTORE_BASE_URL/api/v1/trails" \
              -H "Content-Type: application/json" \
              -H "X-Factstore-CI-Context: circleci" \
              -d "{
                \"flowId\": \"$FACTSTORE_FLOW_ID\",
                \"gitAuthor\": \"$CIRCLE_USERNAME\",
                \"gitAuthorEmail\": \"$CIRCLE_USERNAME@users.noreply.github.com\"
              }"

workflows:
  compliance:
    jobs:
      - factstore-trail
```

Set `FACTSTORE_BASE_URL` and `FACTSTORE_FLOW_ID` as [environment variables](https://circleci.com/docs/env-vars/) in your CircleCI project or organization settings.

---

## Azure DevOps

Add a script task to your `azure-pipelines.yml`. Azure DevOps exposes `BUILD_SOURCEVERSION`, `BUILD_SOURCEBRANCH`, and the build URL can be constructed from `SYSTEM_TEAMFOUNDATIONCOLLECTIONURI`, `SYSTEM_TEAMPROJECT`, and `BUILD_BUILDID`.

```yaml
trigger:
  - main

pool:
  vmImage: ubuntu-latest

variables:
  FACTSTORE_BASE_URL: https://factstore.example.com
  FACTSTORE_FLOW_ID: your-flow-uuid-here

steps:
  - task: Bash@3
    displayName: Create Factstore trail
    inputs:
      targetType: inline
      script: |
        curl -s -X POST "$FACTSTORE_BASE_URL/api/v1/trails" \
          -H "Content-Type: application/json" \
          -H "X-Factstore-CI-Context: azure-devops" \
          -d "{
            \"flowId\": \"$FACTSTORE_FLOW_ID\",
            \"gitAuthor\": \"$(Build.RequestedFor)\",
            \"gitAuthorEmail\": \"$(Build.RequestedForEmail)\"
          }"
```

Store `FACTSTORE_BASE_URL` and any API token as [pipeline variables or variable groups](https://learn.microsoft.com/en-us/azure/devops/pipelines/library/variable-groups) in Azure DevOps Library. Mark secrets as secret variables.

---

## Environment Variable Reference

Full mapping of CI environment variables used by the `X-Factstore-CI-Context` auto-population feature:

| Field | GitHub Actions | GitLab CI | Jenkins | CircleCI | Azure DevOps |
|---|---|---|---|---|---|
| `gitCommitSha` | `GITHUB_SHA` | `CI_COMMIT_SHA` | `GIT_COMMIT` | `CIRCLE_SHA1` | `BUILD_SOURCEVERSION` |
| `gitBranch` | `GITHUB_REF_NAME` | `CI_COMMIT_REF_NAME` | `GIT_BRANCH` | `CIRCLE_BRANCH` | `BUILD_SOURCEBRANCH` |
| `buildUrl` | `GITHUB_SERVER_URL` + `GITHUB_REPOSITORY` + `/actions/runs/` + `GITHUB_RUN_ID` | `CI_JOB_URL` | `BUILD_URL` | `CIRCLE_BUILD_URL` | `SYSTEM_TEAMFOUNDATIONCOLLECTIONURI` + `SYSTEM_TEAMPROJECT` + `/_build/results?buildId=` + `BUILD_BUILDID` |
