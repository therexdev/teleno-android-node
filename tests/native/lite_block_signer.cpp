#include <koinos/crypto/elliptic.hpp>
#include <koinos/crypto/multihash.hpp>
#include <koinos/util/conversion.hpp>
#include <koinos/util/hex.hpp>
#include <koinos/protocol/protocol.pb.h>
#include <iostream>
int main(){auto key=koinos::crypto::private_key::regenerate(koinos::crypto::hash(koinos::crypto::multicodec::sha2_256,std::string("teleno-private-testnet-producer")));std::string line;while(std::getline(std::cin,line)){koinos::protocol::block_header header;auto bytes=koinos::util::from_hex<std::string>(line);if(!header.ParseFromString(bytes))return 2;auto hash=koinos::crypto::hash(koinos::crypto::multicodec::sha2_256,header);std::cout<<koinos::util::to_hex(koinos::util::converter::as<std::string>(hash))<<" "<<koinos::util::to_hex(koinos::util::converter::as<std::string>(key.sign_compact(hash)))<<std::endl;}}
