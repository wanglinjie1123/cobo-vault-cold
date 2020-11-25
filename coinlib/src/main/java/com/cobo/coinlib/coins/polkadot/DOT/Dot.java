/*
 * Copyright (c) 2020 Cobo
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * in the file COPYING.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.cobo.coinlib.coins.polkadot.DOT;

import com.cobo.coinlib.coins.AbsCoin;
import com.cobo.coinlib.coins.AbsDeriver;
import com.cobo.coinlib.coins.AbsTx;
import com.cobo.coinlib.coins.polkadot.AddressCodec;
import com.cobo.coinlib.coins.polkadot.UOS.UOSDecoder;
import com.cobo.coinlib.coins.polkadot.pallets.Pallet;
import com.cobo.coinlib.coins.polkadot.pallets.balance.Transfer;
import com.cobo.coinlib.coins.polkadot.pallets.balance.TransferKeepAlive;
import com.cobo.coinlib.coins.polkadot.pallets.elections_phragmen.Vote;
import com.cobo.coinlib.coins.polkadot.pallets.session.SetKeys;
import com.cobo.coinlib.coins.polkadot.pallets.staking.Bond;
import com.cobo.coinlib.coins.polkadot.pallets.staking.Nominate;
import com.cobo.coinlib.coins.polkadot.pallets.staking.SetController;
import com.cobo.coinlib.coins.polkadot.pallets.staking.Validate;
import com.cobo.coinlib.coins.polkadot.pallets.utility.Batch;
import com.cobo.coinlib.coins.polkadot.pallets.utility.BatchAll;
import com.cobo.coinlib.exception.InvalidTransactionException;
import com.cobo.coinlib.interfaces.Coin;
import com.cobo.coinlib.utils.B58;
import com.cobo.coinlib.utils.Coins;

import org.bouncycastle.util.Arrays;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class Dot extends AbsCoin {
    public static final Map<Integer, Pallet> pallets = new HashMap<>();
    static {
        pallets.put(0x0500, new Transfer(UOSDecoder.POLKADOT));
        pallets.put(0x0503, new TransferKeepAlive(UOSDecoder.POLKADOT));
        pallets.put(0x0900, new SetKeys(UOSDecoder.POLKADOT));
        pallets.put(0x0700, new Bond(UOSDecoder.POLKADOT));
        pallets.put(0x0704, new Validate(UOSDecoder.POLKADOT));
        pallets.put(0x0705, new Nominate(UOSDecoder.POLKADOT));
        pallets.put(0x0708, new SetController(UOSDecoder.POLKADOT));
        pallets.put(0x1a00, new Batch(UOSDecoder.POLKADOT));
        pallets.put(0x1a02, new BatchAll(UOSDecoder.POLKADOT));
        pallets.put(0x1100, new Vote(UOSDecoder.POLKADOT));
    }

    public Dot(Coin impl) {
        super(impl);
    }

    @Override
    public String coinCode() {
        return Coins.DOT.coinCode();
    }

    public static class Tx extends AbsTx {

        public Tx(JSONObject object, String coinCode) throws JSONException, InvalidTransactionException {
            super(object, coinCode);
        }

        @Override
        protected void parseMetaData() throws JSONException {
            to = metaData.getString("dest");
            amount = metaData.getLong("value") / Math.pow(10, decimal);
            fee = metaData.optLong("tip",0) / Math.pow(10, decimal);

            if (!metaData.has("nonce")) {
                metaData.put("nonce",0);
            }
            if (!metaData.has("implVersion")) {
                metaData.put("implVersion",0);
            }
            if (!metaData.has("authoringVersion")) {
                metaData.put("authoringVersion",0);
            }
            metaData.put("eraPeriod",4096);

        }

        @Override
        protected void checkHdPath() throws InvalidTransactionException {
            Coins.Coin coin = Coins.SUPPORTED_COINS.stream()
                    .filter(c->c.coinCode().equals(coinCode))
                    .findFirst().orElse(null);

             if(coin == null || !hdPath.equals(coin.getAccounts()[0])) {
                 throw new InvalidTransactionException(String.format("invalid hdPath \"%s\" for %s", hdPath, coinCode));
             }
        }
    }

    public static class Deriver extends AbsDeriver {
        protected byte prefix = 0;
        @Override
        public String derive(String xPubKey, int changeIndex, int addrIndex) {
            byte[] bytes = new B58().decode(xPubKey);
            byte[] pubKey = Arrays.copyOfRange(bytes,bytes.length - 4 - 32,bytes.length - 4);
            return AddressCodec.encodeAddress(pubKey, prefix);
        }

        @Override
        public String derive(String xPubKey) {
            byte[] bytes = new B58().decode(xPubKey);
            byte[] pubKey = Arrays.copyOfRange(bytes,bytes.length - 4 - 32,bytes.length - 4);
            return AddressCodec.encodeAddress(pubKey, prefix);
        }
    }
}
