# blank

> blank clojure webserver project

## Build/Installation

```
# generate a standalone jar wrapped in an executable script
$ lein bin
```

## Database

[`migratus`](https://github.com/yogthos/migratus) is used for migration management.

```
# create db
$ sudo -u postgres psql -c "create database blank"

# create user
$ sudo -u postgres psql -c "create user blank"

# set password
$ sudo -u postgres psql -c "alter user blank with password 'blank'"

# allow user to create schemas in database
$ sudo -u postgres psql -c "grant create on database blank to blank"

# allow user to create new databases
$ sudo -u postgres psql -c "alter role blank createdb"

# apply migrations from repl
$ lein with-profile +dev repl
user=> (cmd/migrate!)

# running the app from "main" will also apply migrations
# lein run
```

## Usage

```
# start the server
$ export PORT=3003        # default
$ export REPL_PORT=3999   # default
$ bin/blank

# connect to running application
$ lein repl :connect 3999
user=> (initenv)  ; loads a bunch of namespaes
user=> (cmd/migrate!)
```

## Testing

```
# run test
$ lein midje

# or interactively in the repl
$ lein with-profile +dev repl
user=> (autotest)
```

## Docker

```
# build
$ docker build -t blank:latest .
# run
$ docker run --rm -p 3003:3003 -p 3999:3999 --env-file .env blank:latest bin/blank
```

## License

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
