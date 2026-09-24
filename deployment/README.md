# pulse-hive-service deployment skeleton

This is a deployment skeleton for `pulse-hive-service`, prepared by Portal26 so your Spring
Boot service can build, containerize, and deploy to the **same infrastructure** every other
Portal26 service runs on: the same EKS clusters, the same three environments (dev-test, stg,
prod), the same AWS accounts, the same regions, the same Slack build notifications, and the
same arm64 Docker build.

This repo lives inside Portal26's own GitHub org (`titaniam/pulse-hive-service`), on the same
AWS accounts as every other Portal26 service. Everything you need to understand and adapt this
skeleton is in this README and the inline comments in each file. Most values that only
Portal26 could provide (ECR repo name, Slack channel, ServiceAccount name, secret names, ALB
path-prefix, prod cluster name) are already confirmed and filled in directly — what's still
genuinely outstanding is marked `TODO_...` in the file and listed in "Every placeholder in
this skeleton" below.

## What's in this skeleton

```
.github/workflows/
  ci.yml                  Build, test, build+push the arm64 image to stg AND prod ECR,
                           then auto-deploy to stg -> prod on every push to your default branch.
  pr-build.yml             Build + test only, runs on pull requests. Never deploys.
  deploy-dev-manual.yml    Manual (workflow_dispatch) build + deploy to dev-test.
  deploy-stg.yml           Deploys an already-pushed image to staging. Called by ci.yml, or
                           run manually to redeploy a specific version. Chains to deploy-prod.yml.
  deploy-prod.yml          Deploys an already-pushed image to production. Called by
                           deploy-stg.yml, or run manually. Gated by the "production" GitHub environment.

deployment/
  base/deployment.yml      The Kubernetes Deployment shared by every environment.
  base/kustomization.yml   Kustomize base that includes deployment.yml.
  dev-test/kustom/         Kustomize overlay: dev-test image, region, replica count, secrets.
  stg/kustom/              Kustomize overlay: staging image, region, secrets.
  prod/kustom/             Kustomize overlay: production image, resources, secrets, plus hpa.yml.

docker-arm64/
  Dockerfile               Multi-stage arm64 build: distroless Java 25 runtime.
  application.properties   Minimal baked-in Spring config (sample — edit for your app).
  logback.xml              JSON stdout logging config (sample — edit for your app).

src/main/java/com/pulsehive/config/cloud/
  CloudPropertiesFacade.java, AwsAppPropertyLoader.java, SpringPropertyLoader.java,
  AppPropertyLoader.java, PropertyResolver.java
                           The ported AWS Secrets Manager loader — see "Secrets Manager"
                           below. Move/rename the package into your own app; behavior doesn't
                           depend on the package name.
src/main/resources/META-INF/spring.factories
                           Registers CloudPropertiesFacade as a Spring EnvironmentPostProcessor.
                           Update the FQCN in this file if you rename the package above.
```

This mirrors the real layout used by `pulse-partner-gateway`, Portal26's most recently
built Spring Boot service on this platform — including job names, step order, action
versions, and the Kustomize base+overlay structure. Nothing here is a simplified or
"cleaner" version of the real thing; if a step looks unusual (e.g. the hardcoded
`concurrency.group` in the deploy workflows), there's a comment explaining why it's there.

## What Portal26 provides vs. what you provide

| Portal26 provides | You provide |
|---|---|
| ECR repository, `pulse-hive-service` (already confirmed and filled in) | Your Spring Boot application code |
| EKS cluster access (IAM credentials + in-cluster RBAC binding, scoped to this service), already available to this repo as org-level GitHub Actions secrets (see "GitHub Actions secrets you need") | — |
| A **dedicated** Kubernetes ServiceAccount, `hive-service` — **not** the shared `basic-workload` SA other services use. `basic-workload` has account-wide Secrets Manager read; this service must only be able to read its own two secrets (see below), so Portal26 scopes a new SA/IAM binding to just those secret ARNs. Name is confirmed and already set in `deployment/base/deployment.yml`. **Exists and is ready in dev-test** (`arn:aws:iam::427028034291:role/dev-test_hive-service_default_role`, namespace `default`) — **stg and prod are not applied yet**, Portal26 must create those before you can deploy there. | `application.properties` / `logback.xml` tuned for your app (samples included) |
| Two AWS Secrets Manager secrets per environment (see "Secrets Manager" below) — names are confirmed. In dev-test, `pulse.dev-test.hive-service.properties` is created (currently empty — you populate it); stg and prod secrets still need to be created and populated by Portal26 | The ported Secrets Manager loader is already included in this skeleton (`src/main/java/com/pulsehive/config/cloud/`) — wire it into your `build.gradle` (see below); in dev-test, populate `pulse.dev-test.hive-service.properties` yourselves |
| NodePort Service (port **7668**) + ALB Ingress path-prefix registration, in the **internet-facing** ALB group — in Portal26's internal deploy repo, not in this skeleton | Filling in the 2 remaining `TODO_...` placeholders below with the values that are genuinely yours to provide |
| The Slack bot token + channel ID (`C0B3RS2S1R7`, already confirmed and filled in) for build/deploy notifications | — |
| The ALB path-prefix (`/hive/`) — already confirmed, see below | Your Spring context path set to `/hive` so the app actually serves what the ALB routes to it |

**Nothing in this skeleton makes your pod reachable by itself.** The Kubernetes Service and
Ingress are deliberately not part of this repo — on every Portal26 service they live in a
separate internal repo, registered by Portal26. Until Portal26 does that registration for
`pulse-hive-service`, your pod will run but nothing (including the Hive service) can reach
it over the ALB.

Note that unlike `pulse-partner-gateway` (internal-only), `pulse-hive-service`'s ALB group is
**internet-facing** — plan your own authn/authz accordingly; the platform is not putting a
network boundary in front of this service for you.

## Environment / region / account matrix

| Environment | AWS Account ID | ECR Region | EKS Cluster Region | EKS Cluster Name |
|---|---|---|---|---|
| dev-test | `427028034291` | us-east-2 | **ap-south-1** | `dev-test-eks-v36-01` |
| stg      | `427028034291` | us-east-2 | us-east-2 | `stg-eks-v36-01` |
| prod     | `309325302342` | us-east-2 | us-east-2 | `prod-eks-v36-01` |

The dev-test EKS cluster is in a different region (`ap-south-1`) than its ECR registry
(`us-east-2`). This is intentional platform-wide topology — nodes pull the image
cross-region — not a mistake. Don't "fix" it.

dev-test and stg share one AWS account; prod is a separate account. The credentials Portal26
gives you for `STG_AWS_*` are used for **both** dev-test and stg deploys.

## GitHub Actions secrets you need

**You don't need to add these yourself.** All five are `titaniam` **organization-level**
GitHub Actions secrets, not per-repo secrets — every repo in the org inherits them
automatically, the same way `pulse-partner-gateway` does. Confirmed directly against the
GitHub API: `pulse-partner-gateway`'s own repo-level secrets list
(`/repos/titaniam/pulse-partner-gateway/actions/secrets`) is empty (0 secrets), while its
org-secrets list (`/repos/titaniam/pulse-partner-gateway/actions/organization-secrets`)
contains all five of the names below alongside 40+ other org secrets used by other services.

| Secret name | Used for |
|---|---|
| `STG_AWS_ACCESS_KEY_ID` / `STG_AWS_SECRET_ACCESS_KEY` | ECR push + `kubectl apply` against the dev-test/stg account |
| `PROD_AWS_ACCESS_KEY_ID` / `PROD_AWS_SECRET_ACCESS_KEY` | ECR push + `kubectl apply` against the prod account |
| `SLACK_NOTIFICATIONS_BOT_TOKEN` | Build/deploy Slack notifications |

These are static long-lived AWS keys, not OIDC federation — that's how every Portal26
service authenticates today, so this skeleton matches it rather than inventing something
different. Ask Portal26 if you'd prefer OIDC; it isn't set up on this platform yet.

**One caveat:** org secrets in GitHub can be scoped to "all repositories" or to a
"selected repositories" allow-list. Listing secrets by name doesn't reveal which mode is
configured, and confirming that requires org-admin access this investigation didn't have. If
your workflow run fails with an empty/missing credential despite the secret existing at the
org level, the likely cause is that `pulse-hive-service` hasn't been added to that
allow-list yet — that's a one-time action for a `titaniam` org admin, not something you can
do from this repo.

**Before Portal26 can hand you working IAM credentials**, they also need to grant that IAM
identity access inside each EKS cluster (an aws-auth / access-entry mapping to a Kubernetes
Role scoped to just this Deployment). AWS keys alone are not sufficient — `kubectl apply`
will be denied until that in-cluster binding exists. This is **not** something you can set
up yourselves; request it from Portal26 alongside the keys.

## Every placeholder in this skeleton

Only two things are still genuinely outstanding — everything else Portal26 has already
confirmed and filled in directly:

| Placeholder | Appears in | What it needs to become |
|---|---|---|
| `main` (trigger branch) | `ci.yml`, `pr-build.yml` | Your repo's actual default branch, if not `main` |
| `pulse-hive-service-0.0.1-SNAPSHOT.jar` | `ci.yml`, `deploy-dev-manual.yml` | The actual jar filename your `./gradlew bootJar` produces (depends on your `build.gradle` `version`) |

The following are **confirmed by Portal26, not guesses or placeholders** — no action needed:
- GitHub org/repo: `titaniam/pulse-hive-service` — already set in `deploy-stg.yml` and
  `deploy-prod.yml`'s `github.repository ==` guard.
- ECR repository name: `pulse-hive-service`, in both the dev-test/stg account
  (`427028034291`) and the prod account (`309325302342`) — already set in all 5 workflow
  files and all 3 overlay `kustomization.yml` files.
- Slack channel ID: `C0B3RS2S1R7` — the same channel `pulse-partner-gateway` uses for its own
  build/deploy notifications — already set in all 5 workflow files.
- ServiceAccount name: `hive-service` — already set as `serviceAccountName` in
  `deployment/base/deployment.yml`. **Exists and is ready in dev-test**
  (`arn:aws:iam::427028034291:role/dev-test_hive-service_default_role`, namespace `default`).
  **stg and prod are not applied yet** — Portal26 must create those before you can deploy
  there, but nothing blocks your first deploy to dev-test on this front. The role's IAM policy
  is scoped to only this service's two secrets (not the account-wide `basic-workload` access
  other services get): `secretsmanager:GetSecretValue`, `DescribeSecret`, and
  `ListSecretVersionIds` on exactly `pulse.<env>.partner-gateway-auth.properties` and
  `pulse.<env>.hive-service.properties`, plus account-wide `secretsmanager:ListSecrets` (AWS
  doesn't support scoping `ListSecrets` to specific secrets — it only returns names/metadata,
  not values, for every secret in the account). See "What Portal26 provides vs. what you
  provide" and "Order of operations" below.
- ALB path-prefix `/hive/`, NodePort `7668`, internet-facing ALB group — already reflected in
  `deployment/base/deployment.yml`'s probe paths (`/hive/actuator/health`). Your Spring
  context path must be `/hive` to match.
- The two Secrets Manager secret names in all 3 overlay `kustomization.yml` files:
  `pulse.<env>.partner-gateway-auth.properties` and `pulse.<env>.hive-service.properties`
  (see "Secrets Manager" below). **In dev-test, `pulse.dev-test.hive-service.properties` has
  been created but is currently empty — since it's your own service's config (not shared with
  partner-gateway), you populate it yourselves.** stg and prod secrets still need to be
  created and populated before you can deploy there — the ServiceAccount's IAM policy is
  scoped to their ARNs.
- The prod EKS cluster name, `prod-eks-v36-01`, used in `deploy-prod.yml`.
- The five GitHub Actions secrets (`STG_AWS_*`, `PROD_AWS_*`, `SLACK_NOTIFICATIONS_BOT_TOKEN`)
  — these are `titaniam` org-level secrets, already inherited by this repo (see "GitHub
  Actions secrets you need"). No repo-admin action needed to add them.

Nothing else in this skeleton needs to change to get a first deploy working, other than your
own application code and its config.

## Things that are deliberately NOT copied from Portal26's internal template

- **No dependency on Portal26's private Maven repo or internal libraries.** Portal26's own
  services load AWS Secrets Manager values via a private library (`engine-obf`) published to
  a GitHub Packages registry scoped to Portal26's org. Rather than have this service take on
  that dependency, the loader itself has been **ported** into this skeleton
  (`src/main/java/com/pulsehive/config/cloud/`), reimplemented against the public
  `software.amazon.awssdk:secretsmanager` SDK with identical behavior — see "Secrets Manager"
  below. This keeps the build self-contained: no GitHub Packages authentication
  (`GIT_USERNAME`/`GIT_TOKEN`) step, no coupling to a package that's versioned and released
  independently of this service. You don't need to write your own loader, and you don't need
  to add `engine-obf` as a dependency.
- **No OpenAPI spec-lint / contract-drift job.** Portal26's own gateway service enforces its
  API contract in CI; that's specific to that service's design, not a platform requirement,
  so it isn't included here.
- **No Gov Cloud workflow variant.** Another Portal26 service has a Gov Cloud deployment
  path (extra AWS accounts, `us-gov-east-1`). That's a compliance-driven addition specific to
  that service, not something `pulse-hive-service` needs — omitted here.

## Secrets Manager: the ported loader

Every Portal26 service loads its secrets at boot the same way, via a Spring Boot
`EnvironmentPostProcessor` that reads AWS Secrets Manager and merges the results into the
Spring `Environment`. That loader has been **ported into this skeleton** — you don't need to
write your own — at:

```
src/main/java/com/pulsehive/config/cloud/
  CloudPropertiesFacade.java     the EnvironmentPostProcessor entry point
  AwsAppPropertyLoader.java      talks to AWS Secrets Manager
  SpringPropertyLoader.java      wraps the result as a Spring PropertySource
  AppPropertyLoader.java         shared interface + property-name constants
  PropertyResolver.java          System property / env var resolution helper
src/main/resources/META-INF/spring.factories
```

**What changed from Portal26's internal version, and why:** Portal26's own services get
their AWS Secrets Manager client from a private library, `com.titaniamlabs:engine-obf`,
published to a GitHub Packages registry scoped to Portal26's org. Rather than pull that
package in as a dependency, `AwsAppPropertyLoader` has been rewritten to call the public
`software.amazon.awssdk:secretsmanager` SDK and `org.json` directly instead — this keeps the
service self-contained, with no private dependency and no GitHub Packages authentication step
in the build. Every other class is unchanged apart from the package name. The
fetch/merge/failure behavior is identical to what every other Portal26 service does today:

- Reads `pulse.secretsmanager.properties.keyname` (comma-separated secret name **prefixes**)
  and `titaniam.aws.region` — both set as container env vars in the deployment overlays.
- For each prefix, lists all secrets in the account whose name **starts with** that prefix,
  fetches each one's JSON value, and merges all resulting keys into one property set.
- If a prefix matches **zero** secrets, or the merged result is **empty**, the app **fails to
  start** — this is intentional, so a misconfigured secret never fails silently.
- If two matched secrets define the **same key**, startup fails (`putOnce` — duplicate keys
  are a configuration bug, not something to silently resolve).
- Registered with **`addLast`** — i.e. **lowest precedence**. Any value already defined in
  `application.yml`/`application.properties` or a real environment variable wins over the
  Secrets Manager value.
- **Skipped entirely** when the active Spring profile is `test` or `local` — your test suite
  never needs AWS access.

**Gradle dependencies to add** (there is no `build.gradle` in this skeleton — add these to
yours):

```groovy
dependencies {
    implementation platform('software.amazon.awssdk:bom:2.23.0')
    implementation 'software.amazon.awssdk:secretsmanager'
    implementation 'org.json:json:20240303'
}
```

No `engine-obf` dependency, and no GitHub Packages authentication (`GIT_USERNAME`/
`GIT_TOKEN`) is needed for this.

**The two secrets you'll actually get, per environment** (both names below are **confirmed by
Portal26**, not guesses — already wired into `pulse.secretsmanager.properties.keyname` in all
three overlays as a comma-separated pair):

| Secret name | Shared with | Contains |
|---|---|---|
| `pulse.<env>.partner-gateway-auth.properties` | `pulse-partner-gateway` | `gateway.internal-auth.client-id-1` — the value you send as the `X-P26-Internal-Client-Id` header on every call your service makes to partner-gateway |
| `pulse.<env>.hive-service.properties` | (yours only) | `pulse-hive-service`'s own config values |

**The `@ConfigurationProperties` gotcha — read this before you bind anything:** because the
loader is registered with `addLast` (lowest precedence), if you declare a field like:

```yaml
my:
  app:
    some-credential: ${MY_APP_SOME_CREDENTIAL:}
```

that empty `${VAR:}` default **still counts as a defined property value** at
`application.yml`'s (higher) precedence, and will **silently shadow** whatever the Secrets
Manager loader provides — your app will boot with an empty string instead of the real secret,
with no error. Declare Secrets Manager-backed properties as plain dotted names bound via
`@ConfigurationProperties`, with **no placeholder default in `application.yml`/`.properties`
at all**:

```java
@ConfigurationProperties(prefix = "gateway.internal-auth")
public class InternalAuthProperties {
    private String clientId1;
    // getters/setters
}
```

This exactly matches how `pulse-partner-gateway` consumes its own
`gateway.internal-auth.client-id-1` value today.

**IAM:** the dedicated ServiceAccount Portal26 creates for this service (see "What Portal26
provides vs. what you provide") needs `secretsmanager:GetSecretValue` and
`secretsmanager:ListSecrets` on just these two secret ARNs — ask Portal26 to confirm this is
granted before relying on it in a deployed environment.

## Logging & health config

`docker-arm64/application.properties` and `docker-arm64/logback.xml` are minimal working
samples, not fixed requirements — edit them for your app. They demonstrate the two platform
conventions worth keeping:
- Actuator health exposed at `/actuator/health` on the app port (8080), consumed by both the
  ALB healthcheck and the pod's startup/liveness probes.
- Single-line JSON logs to stdout via Logstash's `LogstashEncoder`, so Portal26's shared log
  collector can parse your service's logs the same way it parses everyone else's. This needs
  the `net.logstash.logback:logstash-logback-encoder` dependency in your `build.gradle`.

## Order of operations for your first deploy

1. **You:** finish enough of the Spring Boot app to boot and answer `/actuator/health` (mock
   dependencies as needed — nothing here requires real integrations to exist yet).
2. **Portal26:** creates the `hive-service` ServiceAccount + its IAM binding scoped to the two
   secret ARNs, the dev/stg and prod IAM credentials (with EKS RBAC access already bound), and
   populates the two Secrets Manager secrets per environment with real values. **Your first
   deploy cannot succeed until the ServiceAccount and both secrets actually exist** — the ECR
   repo name, Slack channel, ServiceAccount name, secret names, ALB path-prefix, and prod
   cluster name are all already confirmed and filled in (see "Every placeholder in this
   skeleton" above); what's outstanding here is Portal26 actually provisioning them, not
   naming them.
3. **Nothing to do here:** the required GitHub Actions secrets are already available to this
   repo as `titaniam` org-level secrets (§ "GitHub Actions secrets you need") — no repo-admin
   step needed.
4. **You:** fill in the 2 remaining `TODO_...` placeholders in this skeleton (§ "Every
   placeholder in this skeleton") — your default branch if not `main`, and your actual jar
   filename.
5. **You:** run `deploy-dev-manual.yml` manually (Actions tab → "Run workflow"). This
   validates the whole build → image → deploy pipeline against dev-test in isolation, without
   touching stg or prod.
6. **Portal26:** once dev-test looks right, registers the NodePort Service + Ingress
   path-prefix for `pulse-hive-service` so traffic can actually reach the pod, and confirms
   end-to-end reachability from the Hive service.
7. **You:** merge to your default branch. `ci.yml` now runs the full push → stg → prod chain
   automatically, exactly like Portal26's own services.

**Before step 7 for real:** as shipped, a merge to the default branch deploys to production
automatically with no manual approval gate. **Portal26 requires reviewers to be configured on
the `production` GitHub environment (repo Settings → Environments → `production`) before your
first production-affecting merge.** This is not optional or a "nice to have" — request the
reviewer list from Portal26 and have your repo admin add it before you merge anything that
will reach step 7 for real.

## What we (Portal26) still owe you, not in this skeleton

The ECR repository name, Slack channel ID, ServiceAccount name, secret names, ALB
path-prefix, and prod cluster name are all **already confirmed and filled in** above — what's
still outstanding is Portal26 actually *provisioning* them:

- The `hive-service` ServiceAccount itself (the name is confirmed and already set in
  `deployment/base/deployment.yml`). **Exists and is ready in dev-test**
  (`arn:aws:iam::427028034291:role/dev-test_hive-service_default_role`, namespace `default`),
  scoped to `secretsmanager:GetSecretValue`/`DescribeSecret`/`ListSecretVersionIds` on exactly
  the two secret names listed in "Secrets Manager", plus account-wide `ListSecrets`
  (names/metadata only — AWS doesn't support scoping that action to specific secrets) — not
  the account-wide `basic-workload` access other services get. **stg and prod are not applied
  yet.**
- Working AWS IAM credentials for the dev/stg and prod accounts, each with the necessary
  in-cluster RBAC binding already applied — a raw AWS key pair is not enough on its own.
- The two Secrets Manager secrets themselves (names confirmed), populated with real values,
  per environment. **`pulse.dev-test.hive-service.properties` is created but currently empty
  — it's your own config, so you populate it, not Portal26.** stg and prod still need both the
  ServiceAccount and their secrets before you can deploy there.
- The NodePort Service (port 7668) + internet-facing ALB Ingress registration for the `/hive/`
  path-prefix in our internal deploy repo (without this, your pod deploys but is unreachable).
- Required reviewers configured on the `production` GitHub environment in your repo (see
  "Order of operations" — this is a requirement, not a suggestion).
- Any AWS permissions beyond ECR/EKS/Secrets Manager your service turns out to need (IAM
  policy grants are scoped per-service and added on request, not broad by default).
