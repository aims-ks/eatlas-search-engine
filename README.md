## What is the eAtlasSearchEngine

## Indexation library

We are using ElasticSearch for our indexation library.
ElasticSearch is based on Lucene.

Pros (over Lucene):
* Friendly layer of abstraction
* JSON based documents
* Distributed indexes (if needed)
* Support spatial search using GeoJSON
* Easy to integrate with Cloud base system such as AWS

See: https://www.trustradius.com/compare-products/apache-lucene-vs-elasticsearch

Doc:
https://www.elastic.co/guide/en/elasticsearch/client/java-rest/7.8/index.html

## Personal notes

- TODO list, HOWTO, what I have learn, etc:
  ```
  ~/Desktop/projects/eAtlas-redesign/2020-Drupal9/README.md
  ```

- Starting the Elastic Search engine:
  ```
  $ cd ~/Desktop/projects/Intellij/projects/eatlas-search-engine/
  $ docker-compose up
  ```

- Starting Drupal:
  ```
  $ cd ~/Desktop/projects/eAtlas-redesign/2023-Drupal10
  $ docker-compose up
  ```

- Control panel
  ```
  http://localhost:8080/eatlas-search-engine/admin
  ```
  - Username: `admin`
  - Password: `admin`

  The config file is in:
  ```
  /home/glafond/Desktop/projects/tomcat/etc-tomcat-Catalina-data/eatlas-search-engine/eatlas_search_engine.json
  ```

- Reindex:
  1. Access the control panel (see bellow)
  2. Click on *Reindex* from the left menu
  3. Click the *Reindex all* button at the bottom

- Delete index:

  ```
  $ cd ~/Desktop/projects/Intellij/projects/eatlas-search-engine/
  $ docker-compose down
  $ docker-compose up
  ```

- Testing search engine:
  ```
  http://localhost:8080/eatlas-search-engine/public/search/v1?q=lorem&idx=eatlas_article
  ```

- Drupal website:
  ```
  http://localhost:1010/
  ```

- Deploy

  ```
  $ sudo -u tomcat cp ~/Desktop/projects/Intellij/projects/eatlas-search-engine/target/eatlas-search-engine.war ~/Desktop/projects/tomcat/var-lib-tomcat-webapps/eatlas-search-engine.war
  $ tail -f /var/log/tomcat9/catalina.out
  $ journalctl -n 500 -f -u tomcat9.service
  ```
