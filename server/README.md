# APIs
1. Index page: GET /          (Only for web front-end)
2. Signup page: GET /signup   (Only for web front-end)
3. Log-in page: GET /login    (Only for web front-end)
4. Home page: GET /home       (Only for web front-end)
5. User signup: POST /signup
6. User log-in: POST /login
7. Start VM: GET startvm/:instance
8. Stop VM: GET stopvm/:instance
9. Logout: GET /logout

For detailed implementation see 'server/routes/index.js'.

# Request Body Parameters (JSON)
1. For user sign-up:
  *username: String,
  *password: String,
2. For user log-in:
  *username: String,
  *password: String

(* required parameters)

# Request Route Parameters
1. For starting vm:
  /startvm/:instance  (instance = name of google cloud instance)
2. For stopping vm:
  /stopvm/:instance   (instance = name of google cloud instance)

# Directory structure (For web front end)
1. HTML file to be placed in 'server/views' folder
2. Javascript files to be placed in 'server/public/javascripts' folder
3. Style sheets go to 'server/public/stylesheets' folder
4. Images to be saved at 'server/public/images' folder

# Google cloud:
1. Nodejs and mongodb can be started on instance named 'back-end'.
2. For starting mongodb use "sudo mongod --dbpath /home/mcc_fall_2016_g05/server/data/"
3. For starting nodejs server, cd to 'server' and run 'npm start'
