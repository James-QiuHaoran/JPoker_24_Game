# JPoker_24_Game

This is a distributed game built on java where four players competing with each other to solve a puzzle.

## Architecture and Tech Stack

RMI is used to support the communication between the server and clients. Login, registration and logout function is provided through RMI. JMS is used to support other message delivering and communication between clients and the server. It is asynchronous and safer. JDBC and MySQL is used to build the game database. The game logic is as usual 24-games.
