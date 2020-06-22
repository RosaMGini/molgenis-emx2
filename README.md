[![Build Status](https://travis-ci.org/mswertz/molgenis-emx2.svg?branch=master)](https://travis-ci.org/mswertz/molgenis-emx2)
[![Quality Status](https://sonarcloud.io/api/project_badges/measure?project=mswertz_molgenis-emx2&metric=alert_status)](https://sonarcloud.io/dashboard?id=mswertz_molgenis-emx2)
[![SonarCloud Coverage](https://sonarcloud.io/api/project_badges/measure?project=mswertz_molgenis-emx2&metric=coverage)](https://sonarcloud.io/component_measures/metric/coverage/list?id=mswertz_molgenis-emx2)
[![semantic-release](https://img.shields.io/badge/%20%20%F0%9F%93%A6%F0%9F%9A%80-semantic--release-e10079.svg)](https://github.com/semantic-release/semantic-release)

# molgenis-emx2 preview
This is a reference implementation of MOLGENIS/EMX2 data service. Status: preview (or 'alpha'). 

Demo server: https://emx2.test.molgenis.org/

Design system/styleguide (for app developers): https://mswertz.github.io/molgenis-emx2/

## How to run

You can run EMX2 as:
1. [Docker image](https://hub.docker.com/repository/registry-1.docker.io/mswertz/emx2/tags?page=1) using docker-compose up
1. [java -jar molgenis-emx2-version-all.jar](https://github.com/mswertz/molgenis-emx2/releases) (then you need to install postgresql)
1. Kubernetes Helm Chart

Details below.

### 1. Using docker compose

For local demo we recommend Docker. Therefore install [Docker compose](https://docs.docker.com/compose/install/) and download molgenis-emx2 <a href="https://raw.githubusercontent.com/mswertz/molgenis-emx2/master/docker-compose.yml" download>docker-compose.yml</a> file 

To start, in directory with docker-compose.yml run:
```
docker-compose up
``` 
To update to latest, run:
```console
docker-compose pull
```
Stop by typing ctrl+c.

N.B. 
* because postgres starts slow, emx2 will restart 2-4 times because of 'ConnectException: Connection refused'. This is normal.
* the data of postgresql will be stored in 'psql_data' folder
* if you want particular [molgenis-emx2 version](https://hub.docker.com/repository/registry-1.docker.io/mswertz/emx2/tags?page=1) then add version in docker-compose.yml file 'mswertz/emx2:version'

### 2. Using JAR file and your own postgresql

For minimalist server installation you can use the 'jar' file. 

* Download molgenis-emx2-version-all.jar from [releaes](https://github.com/mswertz/molgenis-emx2/releases).
* Download and install [Postgresql](https://www.postgresql.org/download/) 
* Create postgresql database with name 'molgenis' and with superadmin user/pass 'molgenis'. On Linux/Mac commandline:
    ```console
    sudo -u postgres psql
    postgres=# create database molgenis;
    postgres=# create user molgenis with superuser encrypted password 'molgenis';
    postgres=# grant all privileges on database molgenis to molgenis;
    ```
* Start molgenis-emx2; will run on 8080 (requires java >=11)
    ```console
    java -jar molgenis-emx2-<version>-all.jar
    ```
  
### 3. Using Helm on Kubernetes

If you have Kubernetes server then you can install using [Helm](https://helm.sh/docs/). 

Add helm chart repository (once)
```console
helm repo add emx2 https://mswertz.github.io/molgenis-emx2/helm-charts
```
Run the latest release (see [Helm docs](https://helm.sh/docs/intro/using_helm/))
```console
helm install emx2/emx2
```
Update helm repository to get newest release
```console
helm repo update
```

Alternatively, [download latest helm chart](https://github.com/mswertz/molgenis-emx2/tree/master/docs/helm-charts)

## How to develop

### Basics
We use the following:
* [monorepo](https://en.wikipedia.org/wiki/Monorepo), i.e., all code is in [this repository](https://github.com/mswertz/molgenis-emx2) (it is not a monolith).
* [gradle](https://gradle.org/) for build (with yarn 'workspaces' for web app)
    * ```gradle build``` => builds all
    * ```gradle clean``` => removes all build artifacts
* [Semantic Release](https://github.com/semantic-release/semantic-release) where commit message determines major.minor.patch release 
    * ```fix(component): message``` => results in patch+1 release
    * ```feat(component): message``` => results in minor+1 release
    * ```BREAKING CHANGE: message``` => results in major+1 release
    * ```chore(component): message``` => relates to build process, does not result in release.
    * Other non-release commands: perf, refactor, test, style, docs.
* [github flow](https://guides.github.com/introduction/flow/) which means every pull/merge to master may result in release depending on commit message
* [Travis](https://travis-ci.org/mswertz/molgenis-emx2) to actually execute build+test(+release) for each commit. See .travis.yml file.
* [Sonar](https://sonarcloud.io/dashboard?id=mswertz_molgenis-emx2) for static quality code checks
Major thanks to all these companies!

N.B. snapshot docker images can be found at [Docker hub](https://hub.docker.com/repository/docker/mswertz/emx2-snapshot)
To use particular version in docker-compose.yml change 'mswertz/emx2' to 'mswertz/emx2-snapshot:1.0.27-SNAPSHOT-ba8f51b'

### Code organisation

```
[apps]          # contains javascript apps, one folder per app.
[backend]       # contains java modules, one folder per module. 
[deploy]        # contains sources for helm chart
[docs]          # published at https://mswertz.github.io/molgenis-emx2/
[gradle]        # contains source for gradle
build.gradle    # master build file, typically don't need to edit
settings.gradle # listing of all subprojects for gradle build, edit when adding
docker-compose  # master docker file
gradlew         # platform independent build file
```

### To build all from commandline

Requires local postgresql installation, see above under 'How to run' option 2.

```
git clone https://github.com/mswertz/molgenis-emx2.git
./gradlew build 
```
Takes long first time because download of all dependencies (5 mins on my machine), but much less later (few seconds if you don't change anything) thanks to caches.

### To develop java/backend 'service'

Backend is developed using Java. 
We typically use [IntelliJ IDEA](https://www.jetbrains.com/idea/) for this.
Clone the repo, then open IntelliJ and then 'import' and select the git clone folder.
IntelliJ will recognize gradle and build all. First time that takes a few minutes.

### To develop javascript/frontend 'apps'

Frontend apps are developed using [vuejs](https://vuejs.org/) and [vue-cli](https://cli.vuejs.org/).

To develop, first cd into to folder 'apps' and install all dependencies for all apps.
This also automatically links local dependencies using [yarn workspaces](https://classic.yarnpkg.com/en/docs/workspaces/).
```console
cd apps
yarn install
```

There is one central library called 'styleguide' that contains all shared components (using bootstrap for styling).
To view this run:
```console
cd apps
yarn styleguide
```

All other folders contain apps created using vue-cli.
In order to develop you need to start a molgenis-exm2 backend as described above, e.g. docker-compose up.
The /api and /graphql path is then proxied, see vue.config.js
In order to preview individual apps using yarn serve.
```console
cd apps/schema
yarn serve
```

To create a new app
* use ```vue create [name]```
* add to apps/package.json 'workspaces' so it can be used as dependency
* copy a vue.config.js from another app to have the proxy.
* add to settings.gradle so it is added to the build process

## Guiding principles and features
Below summary of directions that have guided development.

### starting point
* EMX2 simplified metadata format
* Organize data in schemas; each schema functions as permission group and as scope of multi-tenancy if desired
* GraphQL endpoint for each schema, as well as 1 overall
* Uses PostgreSQL for all heavy lifting (incl search, permissions, JSON generation, file storage)
* Can be packaged as one artifact to ease sharing 
* Well isolated components (microfrontend using little spa, we envision microservice for server side add-ons)

### dependencies
* Jooq for safe database interaction 
* Sparkjava for lightweigh webservice
* Jackson for json and csv parsing
* POI for Excel parsing
* graphql-java for graphql api
* OpenApi for web service spec (for file based services that don't use graphql)
To minimize dependencies, no Spring stuff, no Elasticsearch, just the least that can work.
Outside scope: file service, script service, authentication (asumed all to be other services used as dependency)
Most core ideas where already described in https://docs.google.com/document/d/19YEGG8OGgtjCu5WlJKmHbHvosJzw3Mp6e6e7B8EioPY/edit#

### Backend modules
*  emx2: interface and base classes
*  emx2-sql: implementation into postgresql
*  emx2-io: emx2 format, csv import/export of data, legacy import
*  emx2-webservice: web API on top of jooq + io
*  emx2-exampledata: test data models and data, used in various test

### Feature list (mostly in POC or 'walking skeleton' state)
*  simplified EMX '2.0' format 
    - only one 'molgenis.csv' metadata file (instead of now multiple for package, entity, attribute)
    - reducing the width of spreadsheet to only 5 columns: schema, table, column, properties, description
    - the 'properties' column is where all the constraints happen
        - using simple tags as 'int'
        - or parameterised properties as 'ref(otherTable,aColumn)'
    - properties can be defined for schema, table, or column
    - format is designed to be extensible with properties not known in backend
    - rudimentary convertor from EMX1 to EMX2
*  support for multiple schemas
    - schemas probably should be called 'groups'
    - each project/group can get their own schema 
    - each schema will have roles with basic permissions (viewer, editor, creator (for rls), manager, admin)
    - envisioned is that each table will also have these roles, so you can define advanced roles on top
    - row level permission where a 'role' can get edit permission
*  permission systems implemented purely using postgresql permission system
    - role based permission system from postresql (view, edit, manage)
    - users are also roles; 
    - permissions from molgenis perspective are implemented as default roles on schema, table, row level
    - users can adopt these roles
*  extended data definition capabilities
    - simple columnTypes uuid, string, int, decimal, date, datetime, text
    - can create multi-column primary keys and secondary keys ('uniques')
    - can create columns of columnType 'array'
    - can create foreign keys (standard in postgresql)
    - can create arrays of foreign keys (uses triggers)
    - foreign keys can be made to all unique fields, not only primary key (so no mapping between keys needed during import)
        - use cascade updates to circument need for meaningless keys
        - checking of foreign keys is defered to end of transaction to ease consistent batch imports
    - can create multi-column foreign keys (discuss if that is useful)
    - many-to-many relationship produce real tables that can be queried/interacted with
*  reduced frills and limitations in the metadata
    - there is a metadata schema 'molgenis' with schema, table, column metadata tables
    - no advanced columnTypes; envisioned is that those will be defined as property extensions
    - no UI options are known inside data service; again envisioned to be property extensionos
    - no feature for 'labels', items can only have names for schemas, tables, columns
    - freedom in schema, table and column names; they contain spaces and other charachters beyond a-zA-Z09
*  rudimentary import/export for files
    - including molgenis.csv metadata
    - simplified interface to CSV and Excel files (called 'row stores' for now)
*  extended query capabilities
    - can query in joins accross tables by following foreign key references
    - query model similar to trac i.e.
        - each condition can have multiple values, i.e. a or b or c
        - multiple conditions are assumed to be combined with 'and' into one clause
        - multiple clauses can be provided assuming 'or' between them
* basic search capability using Postgresql full text search capability
    - will be very interesting to see how this compares in cost/features to our needs, and elastic.
    - see https://www.postgresql.org/docs/11/textsearch.html
* programmer friendly API (or at least, that is what I think)
    - Rows class enables columnType safe code, with magic columnType conversions
    - assume always sets / arrays / batches
    - reflection based POJO to table mapping, without  heavy metadata / mapping code 
    - no magic with lazy loading and stuff
* simple web service
    - somewhat REST like but tuned to our needs
    - make it easy for non REST specialists to use
    - aim to minimize the number of calls

## Todo and open issues

### first

*  test if drop schema also removes all roles for this schema
*  implement settings
*  implement menu setting so we can standardize menu as a default setting
*  implement JSON data type that we can use in settings (validating form)
*  simple UI to have happy flow out of the box
*  ensure columns are retrieved in same order as they were created
*  add standard an lastUpdated column, as a system column
*  implement order, default on lastUpdated
*  bug, if I filter on refback column it fails, must now select reback.other column
*  upload files directly into postgresql
*  documentation framework so we can start adding some docs

### later
*  create plugin system for services (todo: isolation? runtime loading?)
*  known bug: if I set refback for refarray to 'null' then ref is not updated!
*  validation on queries such that illegal fields are errored (though grahpql prevents this)
*  performance test
*  custom roles
*  more filter option s for array types (now only 'equals')
*  improve error titles and messages
*  seperate upsert from update
*  merge Schema and SchemaMetadata and Table and TableMetadata
*  check that 64char identifier limit doesn't break query aliases that get damn long
*  column level permissions
*  flattened result in graphql for tables, including group by
    *  sorting on nested fields in graphql; showing graphql as flat table
    *  csv result field for that flattened result
*  test and fix the openapi so all the docs just work
*  Search should work on refback columns
*  group by
*  graph mutation next to flat mutation
*  decide if we need 'insert' seperate from 'update'
*  complete metadata mutations
    * delete column
    * rename column, incl triggers
    * rename table, including triggers
*  default limit to 10, maximize on 10.000
*  add a check for maximum limit of identifiers, i.e. 63 characters (Excel limit)
*  Default values
*  Store the descriptions
*  Finish the legacy reader
*  column/per value validation, tuple/per row validation
*  computed values?
*  create validation procedure for Schema/Table/Column so we can give complete error messages and remove model checks from from SQL parts

### someday
*  throw error when webservice is called with only csv header and no values
*  update is actually upsert (insert ... on conflict update) -> can we make it idempotent 'save' (how to update pkey then?)
*  job api to have long running requests wrapped in a job. Should be same as normal api, but then wrapped
*  sanitize column and table identifiers to [_A-Za-z][_0-9A-Za-z] (we support _ to replace all illegal characters)
*  reduce build+test times back to under a minute (LOL)
*  decide to store both ends of ref; added value might be order of items and query speed
*  cross-schema foreign keys, do we need/want those?
*  postgresql queries exposed as readonly tables

