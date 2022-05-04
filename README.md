TODO:

- Login page
  - Auto-focus on username field

- Indexer
  - Message showing how many resources were indexed

- Create files:
  - resources/eatlas_search_engine_default.json

- Fix tests

- Settings page
  - Commit to Git repo (repo dedicated for the search engine config)

- Image
  - Drupal: Create public page, with metadata (with option to make image private; no public page)
  - Indexer: Do not index private image

- Index GIS polygons / points

NOTE: Application name: ${pageContext.servletContext.contextPath}

Example of URL that can't be harvested: https://doi.org/10.1002/aqc.3115

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

- Starting the search engine:
  ```
  $ cd ~/Desktop/projects/Intellij/projects/eatlas-search-engine/
  $ docker-compose up
  ```

- Starting Drupal:
  ```
  $ cd ~/Desktop/projects/eAtlas-redesign/2020-Drupal9
  $ docker-compose up
  ```

- Re-index:

  There is no API for doing this at the moment.
  1. Open the project in IntelliJ
  2. Find the class `au.gov.aims.eatlas.searchengine.Main`
  3. Right-click - Run 'Main.main()'

  The config file is in:
  ```
  /home/glafond/Desktop/projects/tomcat/etc-tomcat-Catalina-data/eatlas-search-engine/eatlas_search_engine.json
  ```

- Delete index:

  ```
  $ docker-compose down
  $ docker-compose up
  ```

- Control panel
  ```
  http://localhost:8080/eatlas-search-engine/admin
  ```

- Testing search engine:
  ```
  http://localhost:8080/eatlas-search-engine/public/search/v1?q=lorem&idx=eatlas_article
  ```

- Drupal website:
  ```
  http://localhost:9090/
  ```

- Deploy

  ```
  $ sudo -u tomcat cp ~/Desktop/projects/Intellij/projects/eatlas-search-engine/target/eatlas-search-engine.war ~/Desktop/projects/tomcat/var-lib-tomcat-webapps/eatlas-search-engine.war
  $ tail -f /var/log/tomcat9/catalina.out
  $ journalctl -n 500 -f -u tomcat9.service
  ```
