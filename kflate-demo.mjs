
import { instantiate } from './kflate-demo.uninstantiated.mjs';


const exports = (await instantiate({
})).exports;

export const {
loadInput,
runCompress,
runDecompress,
getOutput,
getLastError,
memory,
_initialize
} = exports


