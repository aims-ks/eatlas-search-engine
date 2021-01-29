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
  ```~/Desktop/projects/eAtlas-redesign/2020-Drupal9/README.md```

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

- Testing search engine:  
  ```http://localhost:8080/eatlas-search-engine/public/search/v1?q=lorem&idx=eatlas_article```

- Drupal website:  
  ```http://localhost:9090/```
