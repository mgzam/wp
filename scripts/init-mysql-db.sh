#!/usr/bin/env bash
set -x
set -e
set -o nounset
set -o pipefail

MYSQL_DATABASE_TABLES=0
MYSQL_DB_IMPORT_ERROR=0
MYSQL_DATABASE_TOTAL_TABLES=0

WORKDIR="/tmp/wordpress"


mkdir -p $WORKDIR
cd $WORKDIR || exit

# download site content

echo "[INFO] Downloading data SQL files.."
curl -s -L -o sql.zip $ZIP_LINK_SQL
unzip sql.zip -d sql
cd sql || exit

#
# Import Database schema and data
#

# Check if the database is already initialized
echo "[INFO] Check if database is already initialized.."
MYSQL_DATABASE_TOTAL_TABLES=$(
  mysql --host=$MYSQL_HOST \
      --user="$MYSQL_USER" \
      --password="$MYSQL_PASSWORD" \
      --database="$MYSQL_DATABASE" \
      --disable-column-names --batch \
      --execute "SELECT count(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '$MYSQL_DATABASE';"
    )

# Initialize the database if it's empty
if [ "$MYSQL_DATABASE_TOTAL_TABLES" -eq 0 ]; then

  echo "[INFO] Database not initialized"

if [ -f *.sql ]; then
  echo "[INFO] Importing database sql file.."
  mysql --host="$MYSQL_HOST" \
      --user="$MYSQL_USER" \
      --password="$MYSQL_PASSWORD" \
      --database="$MYSQL_DATABASE" < *.sql
      
  if [ "$MYSQL_DB_IMPORT_ERROR" -ne 0 ]; then
    echo "[ERROR] Unexpected error during database schema importation"
    exit 1
  else
    echo "[INFO] Database imported successfully"
  fi
fi
else
  echo "[INFO] Database already initialized"
fi

echo "[INFO] Change the old site URL"
mysql --host="$MYSQL_HOST" \
    --user="$MYSQL_USER" \
    --password="$MYSQL_PASSWORD" \
    --database="$MYSQL_DATABASE"  -e "UPDATE wp_options SET option_value = replace(option_value, '$OLD_URL', '$NEW_URL') WHERE option_name = 'home' OR option_name = 'siteurl'; UPDATE wp_posts SET guid = replace(guid, '$OLD_URL','$NEW_URL'); UPDATE wp_posts SET post_content = replace(post_content, '$OLD_URL', '$NEW_URL'); UPDATE wp_postmeta SET meta_value = replace(meta_value,'$OLD_URL','$NEW_URL');"

cd || exit
rm -rf $WORKDIR || exit
exit 0
