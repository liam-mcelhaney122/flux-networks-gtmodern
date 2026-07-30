package sonar.fluxnetworks.client.design;

import icyllis.modernui.view.*;
import icyllis.modernui.view.ViewGroup.LayoutParams;
import icyllis.modernui.widget.BaseAdapter;
import icyllis.modernui.widget.TextView;
import sonar.fluxnetworks.api.energy.EnergyType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class EnergyTypeAdapter extends BaseAdapter {

    public EnergyTypeAdapter() {
    }

    @Override
    public int getCount() {
        return EnergyType.VALUES.length;
    }

    @Override
    public EnergyType getItem(int position) {
        return EnergyType.VALUES[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, @Nullable View convertView, @Nonnull ViewGroup parent) {
        final TextView view;

        if (convertView == null) {
            view = new TextView(parent.getContext());
        } else {
            view = (TextView) convertView;
        }

        view.setText(getItem(position).getName());

        view.setTextSize(16);
        view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        final int dp4 = view.dp(4);
        view.setPadding(dp4, dp4, dp4, dp4);
        view.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);

        return view;
    }
}
