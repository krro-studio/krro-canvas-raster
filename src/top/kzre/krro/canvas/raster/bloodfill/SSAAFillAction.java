package top.kzre.krro.canvas.raster.bloodfill;

import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.FloatsPools;
import top.kzre.krro.util.tile.AntiAlias;
import top.kzre.krro.util.tile.Canvas;

final class SSAAFillAction implements FillAction {
    private final FillPredicate predicate;
    private final AntiAlias antiAlias;

    public SSAAFillAction(FillPredicate predicate, int scale) {
        this.predicate = predicate;
        antiAlias = AntiAlias.ssaa(scale);
    }

    @Override
    public void apply(double x, double y, Canvas canvas, float[] fillColor) {
        int channels = canvas.getChannels();
        FloatsPool pool = FloatsPools.getPool(channels);
        float[] tmp = pool.acquire();
        try{
            antiAlias.read(tmp, canvas, x, y, fillColor, predicate::shouldFill);
            canvas.setPixel((int) x, (int) y, tmp);
        }
        finally {
            pool.release(tmp);
        }
    }

}