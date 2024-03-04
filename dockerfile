FROM clojure:lein

COPY . /usr/src/app
WORKDIR /usr/src/app

RUN ["lein", "migrate"]

CMD ["lein", "run"]
