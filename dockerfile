# Vue Build
FROM node:21 as ui-build

# Install dependencies first
# That way we aren't rerunning NPM when only source files changed
COPY ui/package.json /usr/src/app/package.json
COPY ui/package-lock.json /usr/src/app/package-lock.json
WORKDIR /usr/src/app
RUN ["npm", "ci"]

# Copy our UI
COPY ui /usr/src/app

# Build our UI
RUN ["npm", "run", "build"]


# Final project
FROM clojure:lein

VOLUME /usr/src/db

# Install lein dependencies first
# That way we have faster partial builds
# (even if our UI changes or any other files change)
COPY project.clj /usr/src/app/project.clj
WORKDIR /usr/src/app
RUN ["lein", "deps"]

# Grab our UI
COPY --from=ui-build /usr/src/app/dist /usr/src/app/resources/public

# Copy our source files
COPY . /usr/src/app
WORKDIR /usr/src/app

# Create our config (generates secrets)
RUN ["lein", "create-conf"]

# Create our database
RUN ["lein", "migrate"]

# Start our server
CMD ["lein", "run"]
