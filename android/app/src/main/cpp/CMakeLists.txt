cmake_minimum_required(VERSION 3.10)
project(termux C)
# PTY helper (JNI) from the Termux terminal-emulator library.
add_library(termux SHARED termux.c)
target_compile_options(termux PRIVATE -std=c11 -Os -fvisibility=hidden)
