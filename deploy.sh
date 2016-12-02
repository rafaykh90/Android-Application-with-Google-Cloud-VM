#!/bin/sh

export path_pwd=`pwd`
export path_rclocal="/etc/rc.local"
export set_credentials="export GOOGLE_APPLICATION_CREDENTIALS=$path_pwd/credentials.json"

# Install MongoDb
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 0C49F3730359A14518585931BC711F9BA15703C6
echo "deb http://repo.mongodb.org/apt/ubuntu trusty/mongodb-org/3.3 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-3.4.list
sudo apt-get update
sudo apt-get install -y mongodb

# Install Nodejs
curl -sL https://deb.nodesource.com/setup_6.x | sudo -E bash -
sudo apt-get install -y nodejs

cd $path_pwd/server

# Install project dependencies
npm install

# Update rc.local to launch at startup
if [ `grep $path_pwd $path_rclocal | wc -l` = 0 ]; then
   # remove last line
   sudo sed -i "s/exit 0//g" $path_rclocal

   sudo sh -c "echo $set_credentials >> $path_rclocal"
   sudo sh -c "echo cd $path_pwd/server >> $path_rclocal"
   sudo sh -c "echo 'npm start >> nodeLog.txt &' >> $path_rclocal"

   # return last line
   sudo sh -c "echo exit 0 >> $path_rclocal"
fi

# Start backend
$set_credentials
npm start >> nodeLog.txt &
echo 
echo Deployment script has completed. Press Enter to continue.

