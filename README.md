# eAtlas Search Engine
The eAtlas Search Engine project provides a configuration interface and functionality to set up and use ElasticSearch
with the eAtlas website.

## How to integrate the search engine into the eAtlas
To integrate the search engine into the eAtlas setup, first, ensure that the eatlas/eatlas-search-engine repository exists by deploying the `ECRResourceStack` stack in the [cdk code](https://github.com/AIMS/AMPSA_infrastructure/).

Next,we will build and tag a Docker image and push it to AWS ECR. You may need to log in to the console and modify you dev user permissions to enable the ECR actions.

```shell
# build docker image
docker build --no-cache -t eatlas/eatlas-search-engine:<VERSION> .

# tag for ECR
docker tag eatlas/eatlas-search-engine:<VERSION> <AWS_ACCOUNT_ID>.dkr.ecr.ap-southeast-2.amazonaws.com/eatlas/eatlas-search-engine:<VERSION>

# push to ECR
aws ecr get-login-password --region ap-southeast-2 | docker login --username AWS --password-stdin <AWS_ACCOUNT_ID>.dkr.ecr.ap-southeast-2.amazonaws.com
docker push <AWS_ACCOUNT_ID>.dkr.ecr.ap-southeast-2.amazonaws.com/eatlas/eatlas-search-engine:<VERSION>
```




---  
  
In the following is the previous readme content which needs to be reviewed and updated:  

## What is the eAtlasSearchEngine

http://localhost:8085/eatlas-search-engine/public/login

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
  http://localhost:8085/eatlas-search-engine/admin
  ```
  - Username: `admin`
  - Password: `admin`

  The config file is in: TODO Location has changed
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

- Check Elastic Search status

  ```
  $ curl -X GET "localhost:9200/_cluster/health?pretty"
  ```

- Change "watermark" thresholds

  Elastic search will prevent writing to disk when the disk is almost full.
  Those thresholds can be changed.
  
  Default:
  ```
  "cluster.routing.allocation.disk.watermark.low": "85%",
  "cluster.routing.allocation.disk.watermark.high": "90%",
  "cluster.routing.allocation.disk.watermark.flood_stage": "95%"
  ```

  ```
  curl -X PUT "localhost:9200/_cluster/settings" -H 'Content-Type: application/json' -d'
  {
    "persistent": {
      "cluster.routing.allocation.disk.watermark.low": "95%",
      "cluster.routing.allocation.disk.watermark.high": "97%",
      "cluster.routing.allocation.disk.watermark.flood_stage": "99%"
    }
  }
  '
  ```

- Hard-reset Elastic Search

  ```
  $ cd ~/Desktop/projects/Intellij/projects/eatlas-search-engine/
  $ docker-compose down
  $ docker system prune -a
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
  $ cp ~/Desktop/projects/Intellij/projects/eatlas-search-engine/target/eatlas-search-engine.war ~/Desktop/projects/Intellij/projects/eatlas-search-engine/webapps/eatlas-search-engine.war
  $ docker logs -f eatlas-searchengine
  ```
