# Keycloak Groups to Vikunja Teams mapper

[![pipeline status](https://rechenknecht.net/giz/keycloak/vikunja-team-mapper/badges/main/pipeline.svg)](https://rechenknecht.net/giz/keycloak/vikunja-team-mapper/-/commits/main)

A Keycloak Mapper that maps Keycloak groups to Vikunja teams. The mapper allows configuration of specific attributes on groups to configure whether

* Teams are public
* How the teams are named
* Which description teams have

This mapper is compatible with Vikunja 0.25 onwards.

## Installation

Place the [generated jar-file](https://rechenknecht.net/giz/keycloak/vikunja-team-mapper/-/jobs/artifacts/main/download?job=build-jar) into the Keycloak deployments folder.
In the Keycloak Quarkus distribution it is located at `/opt/keycloak/providers`.

## Mapper Configuration

To activate the mapper, you must configure it for your clients.

As an example, we add the mapper to the `vikunja` client.

1. Go to the Admin Console, select your client.
1. Go to `Client Scopes`.
1. Click on the `dedicated` scopes entry (should be the first one).
1. Select `Add Mapper` and `By Configuration`.
1. Select `Vikunja team mapper`.
1. Fill in the configuration form. Fill in (or use the default) attribute names on groups.
1. Click `Save`.
1. Go to a group of your choice.
1. Select the `Attributes` tab.
1. Fill in the key for the team name, you configured in your mapper configuration form. Choose a name as value.
1. Click `Save`.

Now, your ID token should include the name of the group you chose.

![Example Vikunja Team Mapper Configuration](docs/mapper-configuration.png)

You may add the other keys to a group as well. However, only a group with a filled in name will be synced to Vikunja.

It is safe to change the values of all Vikunja attributes on groups (even names) as they are synced via the Keycloak Group ID.
