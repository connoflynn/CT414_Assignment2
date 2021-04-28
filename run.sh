#!/bin/bash
clear

echo "Running script"

multiply=400

for i in {1..20}
do
    lines=$((i * multiply))
    echo ""
    echo "Lines of text per thread: $lines"
    java MapReduceAssignment Frankenstein.txt Moby_Dick.txt The_Great_Gatsby.txt $lines 2000
done

echo "Lines of text per thread testing complete"
echo "Starting words per thread testing"


for i in {1..20}
do
    words=$((i * multiply))
    echo ""
    echo "Words per thread: $words"
    java MapReduceAssignment Frankenstein.txt Moby_Dick.txt The_Great_Gatsby.txt 1000 $words
done

echo "Finished"