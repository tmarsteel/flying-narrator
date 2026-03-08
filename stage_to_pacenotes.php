<?php
declare(strict_types=1);

$stage = json_decode(file_get_contents("php://stdin"));

/**
 * let P be the point Origin + $a,
 * then this function returns the angle between the straights Origin->P
 * and P->(Origin+$b). And always the smaller one.
 * @return the angle, -180° - 180°
 */
function vector_angle($a, $b): number {
}
