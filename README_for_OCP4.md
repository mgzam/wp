# Deploy WordPress website on SCALe from an sql dump and website content files

## Kind Reminder

This is a technical Knowledge Base article describing a WP site relocation method that does not take into account any specific configuration a production website may need. In particular it does not cover:
* Specific Wordpress and/or database version requirements
* Extra plugins setup and configuration, especially those requiring callback validation from WP
* DNS,  Secure https (certificates) or users authentication
* Other technical dependencies such as email gateways




## Pre-requisites

To migrate a WordPress website on SCALe, There is the pre-requisites :

* A project in an openshift cluster
* A link to a zip file containing all the needed files, uploads and plugins for WordPress.
* A link to a zip containing SQL dump from your MySQL database.
* The files  in these archive, should not store in subdirectory, they must be accessible directly after extraction.

In order to perform the migration we will use jenkins pipeline, so we'll need to deploy Jenkins on our project.

## How to Deploy Jenkins

> NB 
>pre-requisites:

>If you decide to fork this project on a private repository, you must have an account which does have read access on your new repository and create a secret that you can name jenkins-git-credential 

Access the OpenShift Console and then in the developper view , click +Add => From Catalog.
* Select the CI/CD tab then select Jenkins (Persistent).
* In the opened browser windows, select and then fill the installation template with the correct value especially the Project Name, the Memory Limit and Volume Capacity for data storage then click next.
    * Memory limit: 1Gi
    * Volume Capacity: 1Gi
* Keep Do not bind at this time option checked and then click Create.

After a few minutes, your Jenkins should be up and running and can be accessed via his route. 

## Running Build-pipeline and Deploy pipeline 

* Go to the repository where you can find the  yaml file for the build pipeline yaml: https://github.com/rabah450/wp-mgr
* Copy the yaml from [pipelines/wordpress-build-pipeline.yaml](https://github.com/rabah450/wp-mgr/blob/master/pipeline/wordpress-build-pipeline.yaml)
* Access the OpenShift Console and then click Add on the left pane => YAML.
* Paste the Yaml and then click on create.
* Then +Add => From Catalog => others => wordpres-migration-template
* Fill in with the required parameter.
* In the left pane click Builds you will see to build config, **wordpress-build-pipeline** and **wordpress-deploy-pipeline**
* click on the first one to launch the build wait till it completed and then launch the wordpress deploy pipeline to deploy your wordpress site.

After all those steps, you should have The Database and the Wordpress pod up and running. In order to manage your database i recommend you to deploy phpmyadmin.

## Deploy PhpMyAdmin

To deploy phpmyadmin, follow the following Steps:

* Click +Add => From Container Image

* In image name section past this : **startxfr/openshift-phpmyadmin** . Then click the research icon, the startx image for openshift should appear, next click on create.

* Now the it deployed, in order to connect to database we have to create a configmapin which contain the information to make the connection.
Click on Advanced => ConfigMap => Create ConfigMap:

 - git the name you want 
 - replace the data section by this :
 

      * DB_SERVICE_HOST: "my-wordpress-site-db"
      * DB_SERVICE_PORT: "3306"
      * DB_SERVICE_PWD: "3x0qWrsifMp0"
      * DB_SERVICE_USER: "userebe56428"

**Note: for the DB_SERVICE_PWD and DB_SERVICE_USER, take the information from : Project details => Secret => my-wordpress-site-mysql-secret.**


