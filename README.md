[![Build Status](https://travis-ci.org/mswertz/molgenis-emx2.svg?branch=master)](https://travis-ci.org/mswertz/molgenis-emx2)
[![Quality Status](https://sonarcloud.io/api/project_badges/measure?project=mswertz_molgenis-emx2&metric=alert_status)](https://sonarcloud.io/dashboard?id=mswertz_molgenis-emx2)
[![SonarCloud Coverage](https://sonarcloud.io/api/project_badges/measure?project=mswertz_molgenis-emx2&metric=coverage)](https://sonarcloud.io/component_measures/metric/coverage/list?id=mswertz_molgenis-emx2)

# molgenis-emx2
POC to test some ideas and potentially inform future MOLGENIS developments. In particular this poc 
*  experiments a modular data 'micro' web API service that uses PostgresQL for all heavy lifting
*  minimize dependencies, no Spring stuff, no Elasticsearch (might be add-on services, but too heavy for basics)
*  implements features we currently do not have so we can learn about those
*  explore simple abstractions that match the underlying system (instead of making all be 'repository')
*  outside scope: file service, script service, authentication (asumed all to be other services on top)
The POC uses Jooq as low level database backend, SparkJava for REST-like web services, Jackson for most CSV/IO, and Swagger for API generation
Most core ideas where already described in https://docs.google.com/document/d/19YEGG8OGgtjCu5WlJKmHbHvosJzw3Mp6e6e7B8EioPY/edit#
(I didn't update it recently so I am curious where difference are)

## modules
*  emx2: interface and base classes, concept only
*  emx2-sql: implementation into postgresql
*  emx2-io: emx2 format, csv import/export of data, legacy import
*  emx2-webservice: web API on top of sql + io.
*  emx2-exampledata: test data models and data, used in various test
*  emx2-graphql: incomplete, useless ATM

## how to run
*  need install of postresql 11 with superadmin molgenis/molgenis
*  mvn test is most interesting to see if and how all works
*  emx2-webservice/test TestWebApi is most interesting to play with
*  emx2-io/test/resources/test1.txt gives idea on EMX2 format

## Feature list (mostly in POC or 'walking skeleton' state)
*  support for multiple schemas
    - schemas probably should be called 'groups'
    - each project/group can get their own schema 
    - each schema will have roles with basic permissions (viewer, editor, manager, admin)
    - envisioned is that each table will also have these roles, so you can define advanced roles on top
    - row level permission where a 'role' can get edit permission
*  permission systems implemented purely using postgresql permission system
    - role based permission system from postresql (view, edit, manage)
    - users are also roles; 
    - permissions from molgenis perspective are implemented as default roles on schema, table, row level
    - users can adopt these roles
*  extended data definition capabilities
    - simple types uuid, string, int, decimal, date, datetime, text
    - can create multi-column primary keys and secondary keys ('uniques')
    - can create columns of type 'array'
    - can create foreign keys (standard in postgresql)
    - can create arrays of foreign keys (uses triggers)
    - foreign keys can be made to all unique fields, not only primary key (so no mapping between keys needed during import)
        - use cascade updates to circument need for meaningless keys
        - checking of foreign keys is defered to end of transaction to ease consistent batch imports
    - can create multi-column foreign keys
    - many-to-many relationship produce real tables that can be queried/interacted with
*  simplified EMX '2.0' format 
    - only one 'molgenis.csv' metadata file (instead of now multiple for package, entity, attribute)
    - reducing the width of spreadsheet to only 5 columns: schema, table, column, properties, description
    - the 'properties' column is where all the constraints happen
        - using simple tags as 'int'
        - or parameterised properties as 'ref(otherTable,aColumn)'
    - properties can be defined for schema, table, or column
    - format is designed to be extensible with properties not known in backend
    - rudimentary convertor from EMX1 to EMX2
*  reduced frills and limitations in the metadata
    - there is a metadata schema 'molgenis' with schema, table, column metadata tables
    - no advanced types; envisioned is that those will be defined as property extensions
    - no UI options are known inside data service; again envisioned to be property extensionos
    - no feature for 'labels', items can only have names for schemas, tables, columns
    - freedom in schema, table and column names; they contain spaces and other charachters beyond a-zA-Z09
*  rudimentary import/export for csv.zip files
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
    - Rows class enables type safe code, with magic type conversions
    - assume always sets / arrays / batches
    - reflection based POJO to table mapping, without  heavy metadata / mapping code 
    - no magic with lazy loading and stuff
* simple web service
    - each row has a 'molgenisid' to be used for updates, but that is not necessary primary key
    - somewhat REST like but tuned to our needs
    - make it easy for non REST specialists to use
    - aim to minimize the number of calls

## Todo or consider later (memo to self)
*  many implementations are missing!
*  update is actually upsert (insert ... on conflict update)
*  refactor query to have 'WhereList' for each 'OR' clause and introduce a 'Filter' concept as items in the Where
*  see if WHERE can extend QUERY for better fluent API
*  table or join inheritance
*  ADD LOCAL TRANSACTIONS FOR MULTI_COMMAND OPERATIONS
*  batching of commands
*  enable search on joined tables in query
*  Default values
*  legacy reader
*  implement unqiue and primary key POJO annotations for class->table mapping
``

