#!/bin/bash
# Set HTTP and HTTPS proxy for this shell session

export HTTP_PROXY=http://127.0.0.1:4080
export HTTPS_PROXY=http://127.0.0.1:4080

echo "Proxy configured:"
echo "HTTP_PROXY=$HTTP_PROXY"
echo "HTTPS_PROXY=$HTTPS_PROXY"
