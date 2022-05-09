# unreal-game-sync-badges

## Introduction

unreal-game-sync-badges integrates Jenkins with an Unreal Game Sync (UGS) metadata server, allowing you to publish badges associated with changelists.

## Getting started

unreal-game-sync-badges is tested with [RUGS](https://github.com/jorgenpt/rugs), but should work with the default UGS metadata server. You can find details on configuring UGS on [the official Unreal Engine website](https://docs.unrealengine.com/5.0/en-US/unreal-game-sync-reference-guide-for-unreal-engine/).

### Installation

First, install this plugin on your Jenkins server:

1. Download the `.hpi` file from [the latest release of this plugin](https://github.com/jorgenpt/unreal-game-sync-badges-plugin/releases/latest)
1. On your Jenkins instance, go to `Manage Jenkins`
1. Navigate to `Manage Plugins`
1. Change the tab to `Advanced`
1. Select `Choose File` and point it to the `unreal-game-sync-badges.hpi` you downloaded
1. Click `Deploy` (if you're upgrading, you'll need to restart Jenkins after this step)

![image][img-install-plugin]

### Configuration

It's recommended to configure a global API URL and (if you're using RUGS) credentials:

1. On your Jenkins instance, go to `Manage Jenkins`
1. Go to Configure System
1. Scroll down to `UGS Metadata Server`
1. Set `API URL` to the URL of your metadata server (without the `/api` suffix)
1. (Optional, if you're using RUGS with authentication) Configure a credential
    1. Create a new credential with Kind "Secret Text"
    1. Set the text to "username:password" from the `ci_auth` value from your RUGS instance's `config.json`
    1. Give it a descriptive ID
    1. Configure that ID under `API CI Credential`

![image][img-system-config]

### Using it

At the top of your Pipeline groovy script, add the following import:

```groovy
import io.jenkins.plugins.ugs.BadgeResult;
```

Then, whenever you want to post or update a badge in UGS, use this -- changing the `project` value to be the Perforce path to the folder that contains your `Project.uproject` and setting `result` to the appropriate `BadgeResult` value. You can choose between `STARTING`, `FAILURE`, `WARNING`, `SUCCESS`, and `SKIPPED`. The `name` is the arbitrary name of your badge.

```groovy
postUGSBadge(project: "//depot/branch/Project", changelist: env.P4_CHANGELIST, result: BadgeResult.STARTING, url: env.BUILD_URL, name: 'Editor')
```

## Contributing

Refer to our [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md)

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)


[img-install-plugin]: /docs/install-plugin.png
[img-system-config]: /docs/system-config.png
