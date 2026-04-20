cmake_minimum_required(VERSION 3.22.1)

project("myapplication2")

add_library(
        myapplication2
        SHARED
        native-lib.c)

find_library(
        log-lib
        log)

target_link_libraries(
        myapplication2
        ${log-lib})
