## WillowTree Battlebots

Battlebots is multiplayer game inspired by [scalatron](https://scalatron.github.io/) written in [clojure](https://clojure.org/). 

### How it works

Each player writes their own bot in clojure (other language support may become available in the future). Players then register it in an upcoming game and battle against other bots. 

### Setting up your development environment

#### Requirements

1. [leiningen](http://leiningen.org/)
1. [mongodb](https://docs.mongodb.com/)

#### Getting Started

1. Run `lein run` in root directory (builds project and runs server)
1. Run `lein figwheel` in root directory (compiles clojurescript and watches)
1. Run `lein less auto` in root directory (watches styles)
1. Run `mongod` start mongodb
