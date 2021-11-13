#!/usr/bin/env bash

# sql operations
# select id, ta.name, asset, account from (select id, name from strategy_config where name in ('CELR3S_USDT','AVAX3S_USDT','CRV3S_USDT','YFI3L_USDT')) as ta join (select name, properties::json->>'asset' as asset, account from symbol_info where name in ('CELR3S_USDT','AVAX3S_USDT','CRV3S_USDT','YFI3L_USDT')) as tb on ta.name = tb.name order by name;

if [[ "$#" -eq 0 ]]; then
	#statements
	echo "missing token"
	exit
fi

TOKEN=$1
HOST="dt-test1"

SYMBOL_IDS=(
  2
 69
158
 77
 90
 20
147
  )

SYMBOLS=(
AXS3S_USDT  
DYDX3S_USDT 
OMG3S_USDT  
RSR3S_USDT  
SHIB3S_USDT 
THETA3S_USDT
XMR3S_USDT  
)

function disable_symbol() {
	curl -X POST "http://$HOST/api/v2/strategy/disable?id=$1" \
	-H 'Content-Type: application/json' \
	-H 'exchange: bhex3' \
	-H "Authorization: ${TOKEN}"
}


function enable_symbol() {
	curl -X POST "http://$HOST/api/v2/strategy/enable?id=$1" \
	-H 'Content-Type: application/json' \
	-H 'exchange: bhex3' \
	-H "Authorization: ${TOKEN}"
}

function add_value() {
	curl -X POST "http://$HOST/api/v2/tool/market/launch?name=$1&value=$2" \
	-H 'Content-Type: application/json' \
	-H 'exchange: bhex3' \
	-H "Authorization: ${TOKEN}"
}

function merge_value() {
	curl -X POST "http://$HOST/api/v2/tool/market/merge?name=$1&share=$2" \
	-H 'Content-Type: application/json' \
	-H 'exchange: bhex3' \
	-H "Authorization: ${TOKEN}"
}

# for sym in "${SYMBOLS[@]}"; do
# 	echo $sym;
# 	merge_value $sym 20;
# 	add_value $sym 5700000;

# 	sleep 1;
# done


for id in "${SYMBOL_IDS[@]}"; do
	echo $id;

	# disable_symbol $id;
	enable_symbol $id;
	sleep 2;
done


