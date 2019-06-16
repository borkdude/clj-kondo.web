<?php
header('Content-Type: text/plain');
if (getenv('CLJ_KONDO_DEV')=="true") {
    header('Access-Control-Allow-Origin: *');
}
$code_clj = rawurldecode($_POST["code"]);
$tmp_clj = tempnam("/tmp", "cljc") . ".clj";
file_put_contents($tmp_clj, $code_clj);
ob_start();
$clj_kondo_path=getenv('CLJ_KONDO_PATH');
passthru("$clj_kondo_path --lint $tmp_clj $tmp_cljs --config '{:linters {:unresolved-symbol {:level :error}}}'");
$dat = ob_get_clean();
echo $dat;
unlink($tmp_clj);
?>
