# Technical Challenge
By Matthew Tolman


## Running Locally

The project comes with a dockerfile which takes care of building the UI,
initializing the database and deploying the full project.

The UI and backend live in the same docker container and can be accessed
from the same URL.

The only configuration that docker needs is the port to bind to. It will
generate the rest of the configuration automatically. Below are the
commands to run:

```bash
docker build -t mtolman-tech-challenge .
docker run -p 38080:8080 -it mtolman-tech-challenge
```

Then open up http://localhost:38080.

You will need to create an account using the UI. No accounts are created
by default.

### Running without docker

To run without docker, you need to first install [leiningen](https://leiningen.org/)
and [Node](https://nodejs.org/en). Then run the following commands:

```bash
mkdir -p resources/public && \
cd ui/ && \
npm i && \
npm run build && \
cp -r dist/* ../resources/public/ && \
cd .. && \
lein create-conf && \
lein migrate && \
lein run
```

You can then access the app at http://localhost:8080.

## Tech Stack

For the backend I'm using Clojure with [http-kit](https://github.com/http-kit/http-kit)
as the Java Server and [Reitit](https://github.com/metosin/reitit) as the router. [Logback](https://logback.qos.ch/)
over SL4j is used for logging. [Buddy](https://github.com/funcool/buddy) is
used for cryptography (e.g. password hashing, secret generation, etc.). [SQLite](https://www.sqlite.org/)
is used for the database. I'm also using [Omniconf](https://github.com/grammarly/omniconf) for configuration
management and [KSuid](https://github.com/segmentio/ksuid) for ID generation.

For the frontend I'm using [Vue](https://www.sqlite.org/) and [Vuetify](https://vuetifyjs.com/en/).



